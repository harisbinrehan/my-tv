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
        get() = list("known_apps") ?: (legacyPinned() + SEED_APPS).distinct()
        set(value) = putList("known_apps", value)

    var favourites: List<String>
        get() = list("favourites") ?: legacyPinned()
        set(value) = putList("favourites", value)

    var notificationControls: Boolean
        get() = prefs.getBoolean("notification_controls", true)
        set(value) = prefs.edit().putBoolean("notification_controls", value).apply()

    /** Recent searches typed on the TV, newest first; TvLink keeps anything private-looking out. */
    var recentTyped: List<String>
        get() = list("recent_typed").orEmpty()
        set(value) = putList("recent_typed", value)

    var askedBackground: Boolean
        get() = prefs.getBoolean("asked_background", false)
        set(value) = prefs.edit().putBoolean("asked_background", value).apply()

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

    fun saveLabel(packageName: String, label: String) = prefs.edit().putString("label:$packageName", label).apply()

    fun iconFile(packageName: String): File = File(File(dir, "icons").apply { mkdirs() }, packageName)

    /** The first version kept pinned apps under "apps"; they become favourites. */
    private fun legacyPinned(): List<String> = list("apps").orEmpty()

    private fun list(key: String): List<String>? = prefs.getString(key, null)?.split('\n')?.filter { it.isNotBlank() }

    private fun putList(key: String, value: List<String>) = prefs.edit().putString(key, value.joinToString("\n")).apply()

    private companion object {
        val SEED_APPS = listOf("com.release.aryzaptv", "com.google.android.youtube.tv")
    }
}
