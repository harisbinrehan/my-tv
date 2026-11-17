package com.shoqremote.app

import android.content.Context
import com.shoqremote.atv.ClientIdentity
import com.shoqremote.atv.ShoqBox
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** App-private storage: the remote's key, pairing state, the box's address, its apps and their Play Store details. */
class Storage(context: Context) {
    private val dir = context.filesDir
    private val prefs = context.getSharedPreferences("shoq-remote", Context.MODE_PRIVATE)

    init {
        upgradeFromBefore15()
    }

    fun identity(deviceName: String): ClientIdentity {
        val key = File(dir, "client.key")
        val cert = File(dir, "client.crt")
        if (key.exists() && cert.exists()) return ClientIdentity.load(key.readBytes(), cert.readBytes())
        return ClientIdentity.generate("My Tv ($deviceName)").also {
            key.writeBytes(it.keyBytes)
            cert.writeBytes(it.certificateBytes)
        }
    }

    fun forgetPairing() {
        paired = false
        File(dir, "client.key").delete()
        File(dir, "client.crt").delete()
    }

    var paired: Boolean
        get() = prefs.getBoolean("paired", false)
        set(value) = prefs.edit().putBoolean("paired", value).apply()

    var host: String
        get() = prefs.getString("host", null) ?: ShoqBox.DEFAULT_HOST
        set(value) = prefs.edit().putString("host", value).apply()

    /** Apps the box has shown on screen, in the order they first appeared. */
    var apps: List<String>
        // The screensaver was listed by mistake before version 18; it is never an app.
        get() = (list("known_apps") ?: (legacyPinned() + SEED_APPS).distinct()) - SCREENSAVER
        set(value) = putList("known_apps", value)

    var favourites: List<String>
        get() = list("favourites") ?: legacyPinned()
        set(value) = putList("favourites", value)

    var notificationControls: Boolean
        get() = prefs.getBoolean("notification_controls", true)
        set(value) = prefs.edit().putBoolean("notification_controls", value).apply()

    var askedBackground: Boolean
        get() = prefs.getBoolean("asked_background", false)
        set(value) = prefs.edit().putBoolean("asked_background", value).apply()

    /** The versionCode the screens last opened with; 0 before version 10 recorded it. */
    var lastVersionCode: Long
        get() = prefs.getLong("last_version_code", 0L)
        set(value) = prefs.edit().putLong("last_version_code", value).apply()

    var channels: List<TvChannel>
        get() = prefs.getString("channels", null)?.let { json ->
            runCatching {
                val array = JSONArray(json)
                (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    TvChannel(item.getInt("n"), item.getString("name"), item.optString("cat", "Other"))
                }
            }.getOrNull()
        } ?: emptyList()
        set(value) {
            val array = JSONArray()
            value.forEach { array.put(JSONObject().put("n", it.number).put("name", it.name).put("cat", it.category)) }
            prefs.edit().putString("channels", array.toString()).apply()
        }

    /** Pinned channel numbers in the user's order. */
    var pinnedChannels: List<Int>
        get() = list("pinned_channels")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        set(value) = putList("pinned_channels", value.map { it.toString() })

    fun label(packageName: String): String? = prefs.getString("label:$packageName", null)

    /** Google Play has no page for this app (it was installed from elsewhere), so it opens by voice. */
    fun noPlayPage(packageName: String): Boolean = prefs.getBoolean("noplay:$packageName", false)

    fun markNoPlayPage(packageName: String) = prefs.edit().putBoolean("noplay:$packageName", true).apply()

    /** A code to type when this app asks for one (for example an OTP that stays the same). */
    fun appCode(packageName: String): String? = prefs.getString("code:$packageName", null)

    fun setAppCode(packageName: String, code: String?) =
        prefs.edit().apply { if (code == null) remove("code:$packageName") else putString("code:$packageName", code) }.apply()

    fun saveLabel(packageName: String, label: String) = prefs.edit().putString("label:$packageName", label).apply()

    fun iconFile(packageName: String): File = File(File(dir, "icons").apply { mkdirs() }, packageName)

    /**
     * Version 15 stopped keeping recent searches (this clears the ones saved before) and lists Netflix and the
     * Play Store from the start (added once to lists made before, so removing them later sticks).
     */
    private fun upgradeFromBefore15() {
        if (prefs.contains("recent_typed")) prefs.edit().remove("recent_typed").apply()
        if (!prefs.getBoolean("added_v15_apps", false)) {
            apps = (apps + ADDED_IN_15).distinct()
            prefs.edit().putBoolean("added_v15_apps", true).apply()
        }
    }

    /** The first version kept pinned apps under "apps"; they become favourites. */
    private fun legacyPinned(): List<String> = list("apps").orEmpty()

    private fun list(key: String): List<String>? = prefs.getString(key, null)?.split('\n')?.filter { it.isNotBlank() }

    private fun putList(key: String, value: List<String>) = prefs.edit().putString(key, value.joinToString("\n")).apply()

    private companion object {
        val ADDED_IN_15 = listOf("com.netflix.ninja", "com.android.vending")
        val SEED_APPS = listOf("com.release.aryzaptv", "com.google.android.youtube.tv") + ADDED_IN_15
    }
}
