package com.shoqremote.atv

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** What an app on the box reports through Cast: the video's title and channel, play state and position. */
data class CastMediaInfo(
    /** Cast's name for the app, for example "YouTube". */
    val app: String,
    val title: String?,
    /** The channel, artist or series. */
    val subtitle: String?,
    /** True while playing (or buffering), false while paused, null when the app has nothing loaded. */
    val playing: Boolean?,
    val positionSec: Double?,
    val durationSec: Double?,
    val live: Boolean,
    /** When [positionSec] was read (System.currentTimeMillis), to move the position on while it plays. */
    val readAtMs: Long,
)

/**
 * Follows what's playing on the box through its built-in Cast receiver. Apps with Cast support (YouTube does) list
 * a session there even when opened on the TV itself, and answer the media channel with what they play. This only
 * reads: it never launches, stops or controls anything, so the TV is never disturbed.
 *
 * Cast receivers present their own device certificates, so the server isn't checked here: nothing is sent but
 * status requests, and the app only uses this for a host that has passed the remote service's pinned certificate.
 */
class CastMedia(private val host: String, private val port: Int = CastWake.PORT, private val log: (String) -> Unit = {}) {
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var closed = false

    /** Reports each change (null: nothing playing) until [close], or throws when the connection fails. */
    fun run(onChange: (CastMediaInfo?) -> Unit) {
        val socket = open()
        this.socket = socket
        if (closed) return socket.close()
        socket.use {
            val out = socket.outputStream
            fun send(namespace: String, json: String, destination: String = RECEIVER_ID) {
                out.write(CastWake.frame(namespace, json, destination = destination))
                out.flush()
            }
            val frames = CastWake.FrameReader()
            val chunk = ByteArray(32 * 1024)
            var transport: String? = null
            var app: String? = null
            var media: Map<*, *>? = null
            var last: CastMediaInfo? = null
            var reported = false
            var requestId = 1
            var lastPing = 0L
            var lastMediaPoll = 0L
            var lastReceiverPoll = 0L

            fun report(info: CastMediaInfo?) {
                if (reported && info == last) return
                reported = true
                last = info
                onChange(info)
            }

            send(CastWake.CONNECTION, """{"type":"CONNECT"}""")
            send(CastWake.RECEIVER, """{"type":"GET_STATUS","requestId":${requestId++}}""")
            lastReceiverPoll = now()
            while (!closed && !Thread.currentThread().isInterrupted) {
                val read = try {
                    socket.inputStream.read(chunk)
                } catch (e: SocketTimeoutException) {
                    0
                }
                if (read < 0) throw IOException("the box closed the Cast connection")
                for ((namespace, payload) in frames.add(chunk, read)) {
                    when (namespace) {
                        CastWake.HEARTBEAT -> if (payload.contains("\"PING\"")) send(CastWake.HEARTBEAT, """{"type":"PONG"}""")
                        CastWake.RECEIVER -> {
                            val json = MiniJson.parseOrNull(payload) as? Map<*, *> ?: continue
                            if (json["type"] != "RECEIVER_STATUS") continue
                            val running = runningApp(json)
                            val id = running?.second
                            if (id != transport) {
                                transport = id
                                app = running?.first
                                media = null
                                log("Cast app: ${app ?: "none"}")
                                if (id == null) {
                                    report(null)
                                } else {
                                    send(CastWake.CONNECTION, """{"type":"CONNECT"}""", id)
                                    send(MEDIA, """{"type":"GET_STATUS","requestId":${requestId++}}""", id)
                                    lastMediaPoll = now()
                                }
                            }
                        }
                        MEDIA -> {
                            val name = app ?: continue
                            val json = MiniJson.parseOrNull(payload) as? Map<*, *> ?: continue
                            if (json["type"] != "MEDIA_STATUS") continue
                            val status = (json["status"] as? List<*>)?.firstOrNull() as? Map<*, *>
                            // Updates leave out "media" when it hasn't changed, so the last one is kept.
                            (status?.get("media") as? Map<*, *>)?.let { media = it }
                            if (status == null) media = null
                            report(infoOf(name, status, media, System.currentTimeMillis()))
                        }
                    }
                }
                val t = now()
                if (t - lastPing >= PING_EVERY_MS) {
                    send(CastWake.HEARTBEAT, """{"type":"PING"}""")
                    lastPing = t
                }
                val id = transport
                if (id != null && t - lastMediaPoll >= MEDIA_POLL_MS) {
                    send(MEDIA, """{"type":"GET_STATUS","requestId":${requestId++}}""", id)
                    lastMediaPoll = t
                }
                if (t - lastReceiverPoll >= RECEIVER_POLL_MS) {
                    send(CastWake.RECEIVER, """{"type":"GET_STATUS","requestId":${requestId++}}""")
                    lastReceiverPoll = t
                }
            }
        }
    }

    fun close() {
        closed = true
        runCatching { socket?.close() }
    }

    private fun open(): SSLSocket {
        val trustAny = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAny), null) }
        val plain = Socket()
        try {
            plain.connect(InetSocketAddress(host, port), 5_000)
            plain.soTimeout = 5_000
            plain.tcpNoDelay = true
            val socket = context.socketFactory.createSocket(plain, host, port, true) as SSLSocket
            socket.startHandshake()
            socket.soTimeout = 500
            return socket
        } catch (e: IOException) {
            plain.close()
            throw e
        }
    }

    private fun now() = System.nanoTime() / 1_000_000

    companion object {
        internal const val MEDIA = "urn:x-cast:com.google.cast.media"
        private const val RECEIVER_ID = "receiver-0"
        private const val PING_EVERY_MS = 5_000L
        private const val MEDIA_POLL_MS = 15_000L
        private const val RECEIVER_POLL_MS = 30_000L

        /** The (name, transportId) of the app the receiver lists as running, unless it's the idle screen. */
        internal fun runningApp(receiverStatus: Map<*, *>): Pair<String, String>? {
            val status = receiverStatus["status"] as? Map<*, *> ?: return null
            val apps = status["applications"] as? List<*> ?: return null
            return apps.asSequence()
                .mapNotNull { it as? Map<*, *> }
                .filter { it["isIdleScreen"] != true }
                .mapNotNull { app ->
                    val transport = app["transportId"] as? String ?: return@mapNotNull null
                    val name = app["displayName"] as? String ?: app["statusText"] as? String ?: "App"
                    name to transport
                }
                .firstOrNull()
        }

        /** One media status entry (and the last media it described) as what the app is playing. */
        internal fun infoOf(app: String, status: Map<*, *>?, media: Map<*, *>?, readAtMs: Long): CastMediaInfo? {
            if (status == null) return null
            val metadata = media?.get("metadata") as? Map<*, *>
            fun text(key: String) = (metadata?.get(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val playing = when (status["playerState"]) {
                "PLAYING", "BUFFERING" -> true
                "PAUSED" -> false
                else -> null
            }
            val title = text("title") ?: text("seriesTitle")
            if (playing == null && title == null) return null
            val duration = (media?.get("duration") as? Number)?.toDouble()?.takeIf { it > 0 }
            return CastMediaInfo(
                app = app,
                title = title,
                subtitle = text("artist") ?: text("subtitle") ?: text("albumArtist") ?: text("seriesTitle")?.takeIf { it != title },
                playing = playing,
                positionSec = (status["currentTime"] as? Number)?.toDouble(),
                durationSec = duration,
                live = media?.get("streamType") == "LIVE",
                readAtMs = readAtMs,
            )
        }
    }
}

/** Just enough JSON for Cast status messages: objects, arrays, strings, numbers, true, false and null. */
internal object MiniJson {
    fun parseOrNull(text: String): Any? = runCatching { parse(text) }.getOrNull()

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.space()
        require(reader.done()) { "trailing characters" }
        return value
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun done() = i >= s.length

        fun space() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            space()
            require(i < s.length) { "unexpected end" }
            return when (s[i]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> number()
            }
        }

        private fun word(w: String, v: Any?): Any? {
            require(s.startsWith(w, i)) { "bad literal at $i" }
            i += w.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++
            space()
            if (s[i] == '}') return map.also { i++ }
            while (true) {
                space()
                val key = string()
                space()
                require(s[i] == ':') { "expected : at $i" }
                i++
                map[key] = value()
                space()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw IllegalArgumentException("expected , or } at ${i - 1}")
                }
            }
        }

        private fun array(): List<Any?> {
            val list = ArrayList<Any?>()
            i++
            space()
            if (s[i] == ']') return list.also { i++ }
            while (true) {
                list += value()
                space()
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw IllegalArgumentException("expected , or ] at ${i - 1}")
                }
            }
        }

        private fun string(): String {
            require(s[i] == '"') { "expected string at $i" }
            i++
            val out = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            out.append(s.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> out.append(e)
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }
    }
}
