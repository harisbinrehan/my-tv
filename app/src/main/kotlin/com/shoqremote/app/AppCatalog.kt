package com.shoqremote.app

import android.graphics.BitmapFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AppMeta(val label: String, val iconPath: String?)

private val KNOWN_LABELS = mapOf(
    "com.google.android.youtube.tv" to "YouTube",
    "com.release.aryzaptv" to "ARY Plus",
    "com.android.tv.settings" to "Settings",
    "com.android.vending" to "Play Store",
    "com.netflix.ninja" to "Netflix",
    "com.launcher.ptcl" to "Home",
    "com.zte.CTBrowser" to "Live TV",
    "com.google.android.katniss" to "Google Assistant",
)

/** A readable name before the Play Store details arrive, or for apps that have no Play Store page. */
fun fallbackLabel(packageName: String): String =
    KNOWN_LABELS[packageName] ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

/** Official app names and icons, read from each app's Google Play page and cached on the phone. */
class AppCatalog(private val storage: Storage) {

    fun cached(packageName: String): AppMeta? {
        val label = storage.label(packageName) ?: return null
        return AppMeta(label, storage.iconFile(packageName).takeIf { it.exists() }?.path)
    }

    /** Blocking; null when the app has no Play Store page or the phone is offline. */
    fun fetch(packageName: String): AppMeta? = runCatching {
        val page = get("https://play.google.com/store/apps/details?id=$packageName&hl=en&gl=US").toString(Charsets.UTF_8)
        val title = meta(page, "og:title")?.let(::cleanTitle)?.takeIf { it.isNotBlank() } ?: return null
        val iconFile = storage.iconFile(packageName)
        meta(page, "og:image")?.let { image ->
            val bytes = get(image.substringBefore('=') + "=s256")
            if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) iconFile.writeBytes(bytes)
        }
        storage.saveLabel(packageName, title)
        AppMeta(title, iconFile.takeIf { it.exists() }?.path)
    }.getOrNull()

    private fun get(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept-Language", "en")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun meta(page: String, property: String): String? =
        Regex("""<meta property="$property" content="([^"]*)"""").find(page)?.groupValues?.get(1)?.let(::unescape)

    private fun cleanTitle(title: String): String =
        title.removeSuffix(" - Apps on Google Play").replace(TV_SUFFIX, "").trim()

    private fun unescape(text: String): String =
        text.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        val TV_SUFFIX = Regex(""" (for|on) (Android|Google) TV$""")
    }
}
