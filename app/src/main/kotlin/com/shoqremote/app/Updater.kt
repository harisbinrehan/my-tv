package com.shoqremote.app

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Where releases are published (`release.sh`); the app reads `update.json` from the newest one. */
const val RELEASES_REPO = "harisbinrehan/my-tv"
private const val UPDATE_URL = "https://github.com/$RELEASES_REPO/releases/latest/download/update.json"

data class Release(val versionCode: Long, val versionName: String, val url: String, val sha256: String, val notes: String)

data class UpdateState(
    val installedName: String = "",
    val available: Release? = null,
    /** Download progress, 0 to 100, while downloading; null otherwise. */
    val progress: Int? = null,
    val installing: Boolean = false,
    val dismissed: Boolean = false,
)

/**
 * Updates from inside the app. It reads `update.json` from the latest GitHub release and offers a newer version;
 * the APK is downloaded over HTTPS, checked against the SHA-256 in `update.json`, then handed to Android's
 * installer. Android asks the owner to confirm and only accepts an APK signed with the installed app's key, so
 * pairing and settings carry over and nothing else can replace the app.
 */
class Updater(private val app: Application, private val notify: (String) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installed = app.packageManager.getPackageInfo(app.packageName, 0)
    private val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()
    private val _state = MutableStateFlow(UpdateState(installedName = installed.versionName.orEmpty()))
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null
    @Volatile private var lastCheck = 0L

    /** On every return to the app, at most every 15 minutes; [manual] always checks and always answers. */
    fun check(manual: Boolean = false) {
        if (job?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (!manual && lastCheck != 0L && now - lastCheck < CHECK_EVERY_MS) return
        lastCheck = now
        job = scope.launch {
            try {
                val release = fetchRelease()
                Trace.i("update check: newest is ${release.versionName} (${release.versionCode}), installed ${installed.versionName} ($installedCode)")
                if (release.versionCode > installedCode) {
                    _state.update { it.copy(available = release, dismissed = if (manual) false else it.dismissed && it.available == release) }
                } else {
                    _state.update { it.copy(available = null) }
                    if (manual) notify("My Tv is up to date (version ${installed.versionName}).")
                }
            } catch (e: Exception) {
                Trace.w("update check failed", e)
                if (manual) notify("Couldn't check for updates. Check the internet connection and try again.")
            }
        }
    }

    fun dismiss() = _state.update { it.copy(dismissed = true) }

    /** Downloads, verifies and installs [release]; Android shows its own confirmation. */
    fun install(context: Context) {
        val release = _state.value.available ?: return
        if (job?.isActive == true) return
        if (!app.packageManager.canRequestPackageInstalls()) {
            Trace.i("update: asking for permission to install updates")
            notify("Allow My Tv to install updates, then come back and tap Update again.")
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            runCatching { context.startActivity(settings) }
            return
        }
        job = scope.launch {
            val file = File(app.cacheDir, "update.apk")
            try {
                _state.update { it.copy(progress = 0) }
                Trace.i("update: downloading ${release.versionName}")
                download(release, file)
                _state.update { it.copy(progress = null, installing = true) }
                Trace.i("update: verified, handing ${file.length()} bytes to the installer")
                commit(file)
            } catch (e: Exception) {
                Trace.w("update failed", e)
                _state.update { it.copy(progress = null, installing = false) }
                notify(if (e is DamagedDownload) "The update download was damaged. Tap Update to try again." else "The update didn't download. Check the internet connection and try again.")
            } finally {
                file.delete()
            }
        }
    }

    /** The installer's answer: a confirmation to show, or why it failed. Success restarts the app. */
    fun onInstallerResult(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                Trace.i("update: Android is asking to confirm")
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Trace.i("update: installed")
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Trace.w("update: installer said $status $why")
                _state.update { it.copy(installing = false) }
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) notify("The update didn't install ($why).")
            }
        }
    }

    private fun fetchRelease(): Release {
        val json = JSONObject(get(UPDATE_URL).use { it.inputStream.bufferedReader().readText() })
        return Release(
            versionCode = json.getLong("versionCode"),
            versionName = json.getString("versionName"),
            url = json.getString("url"),
            sha256 = json.getString("sha256").lowercase(),
            notes = json.optString("notes"),
        )
    }

    private fun download(release: Release, file: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        get(release.url).use { connection ->
            val total = connection.contentLengthLong
            var done = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0) _state.update { it.copy(progress = (done * 100 / total).toInt()) }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != release.sha256) throw DamagedDownload("expected ${release.sha256.take(12)}, got ${actual.take(12)}")
    }

    private fun commit(file: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val result = PendingIntent.getBroadcast(app, id, Intent(app, UpdateReceiver::class.java).setPackage(app.packageName), flags)
            session.commit(result.intentSender)
        }
    }

    /** One GET, following GitHub's redirect to its download host; HTTPS only. */
    private fun get(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/octet-stream, application/json")
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("HTTP $code from ${URL(url).host}")
        }
        if (connection.url.protocol != "https") {
            connection.disconnect()
            throw IOException("refused a non-HTTPS download")
        }
        return connection
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T = try {
        block(this)
    } finally {
        disconnect()
    }

    private class DamagedDownload(message: String) : IOException(message)

    private companion object {
        const val CHECK_EVERY_MS = 15 * 60 * 1000L
    }
}

/** Receives the installer's status for an update the app started. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as MyTvApp).updater.onInstallerResult(context, intent)
    }
}
