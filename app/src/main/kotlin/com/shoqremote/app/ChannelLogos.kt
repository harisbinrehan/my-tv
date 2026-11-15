package com.shoqremote.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import org.json.JSONObject

/**
 * Channel logos by name. `assets/channel_logos.json` maps a normalised channel name to a logo address from the
 * public iptv-org database (built for PTCL Shoq's line-up plus every Pakistani channel). Each logo is downloaded
 * once over HTTPS and kept in the app's files, so the list shows them offline afterwards.
 */
class ChannelLogos(private val app: Application) {
    private val urls: Map<String, String> by lazy { load() }
    private val dir = File(app.filesDir, "logos")
    private val failed = Collections.synchronizedSet(HashSet<String>())

    fun has(name: String): Boolean = urls.containsKey(normaliseChannelName(name))

    /** Blocking: the logo, sized for a list row; null when the channel has none or it can't be fetched now. */
    fun bitmap(name: String): Bitmap? {
        val key = normaliseChannelName(name)
        val url = urls[key] ?: return null
        val file = File(dir, "$key.img")
        if (!file.exists()) {
            if (key in failed) return null
            if (!download(url, file)) {
                failed += key
                return null
            }
        }
        return decode(file)
    }

    private fun download(url: String, file: File): Boolean = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "MyTv (Android; channel logos)")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK || connection.url.protocol != "https") return false
            val bytes = connection.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return false
            dir.mkdirs()
            file.writeBytes(bytes)
            true
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    /** Logos come as large as 1500 px; a row shows about 64 dp, so decode near 256 px wide. */
    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 256) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun load(): Map<String, String> = runCatching {
        val json = JSONObject(app.assets.open("channel_logos.json").bufferedReader().use { it.readText() })
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())
}

private val DROPPED_WORDS = setOf("hd", "tv", "the")

/** Same rule as the script that built the asset: lower case, & and + spelled out, no hd/tv/the, no spaces or symbols. */
internal fun normaliseChannelName(name: String): String =
    name.lowercase().replace("&", " and ").replace("+", " plus ")
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() && it !in DROPPED_WORDS }
        .joinToString("")
