package com.shoqremote.atv

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Turns the box on through its built-in Chromecast receiver, which, unlike the remote service, needs no pairing.
 * Launching Cast's default receiver brings the box out of standby (HDMI-CEC then switches the TV on to the box's
 * input), and stopping it again leaves the home screen. A box that's already on is left alone, so this never
 * interrupts what's playing. On the B866V2F it woke the box from a fresh standby; see CLAUDE.md for its record.
 *
 * Cast receivers present their own device certificates, so the server isn't checked here: nothing private is sent,
 * and the app only calls this for a host that has already passed the remote service's pinned certificate.
 */
object CastWake {
    const val PORT = 8009

    enum class Result { WOKE, ALREADY_ON, FAILED }

    internal const val DEFAULT_RECEIVER = "CC1AD845"
    internal const val CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
    internal const val HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
    internal const val RECEIVER = "urn:x-cast:com.google.cast.receiver"

    /**
     * The receiver stays up until the box says it's awake, or this long at most. Stopping it the moment it appeared
     * left the box asleep on one try out of two from a longer standby, so it isn't stopped early any more.
     */
    internal const val HOLD_MS = 10_000L

    fun wake(
        host: String,
        port: Int = PORT,
        timeoutMs: Long = 20_000,
        holdMs: Long = HOLD_MS,
        log: (String) -> Unit = {},
    ): Result {
        val start = System.nanoTime()
        fun elapsed() = (System.nanoTime() - start) / 1_000_000
        val socket = try {
            open(host, port, 5_000)
        } catch (e: IOException) {
            log("no Cast answer: ${e.message}")
            return Result.FAILED
        }
        socket.use {
            val out = socket.outputStream
            fun send(namespace: String, json: String) {
                out.write(frame(namespace, json))
                out.flush()
            }
            val frames = FrameReader()
            val chunk = ByteArray(16 * 1024)
            var standby: Boolean? = null
            var launchedAt = -1L
            var session: String? = null
            var stopped = false
            var woke = false

            send(CONNECTION, """{"type":"CONNECT"}""")
            send(RECEIVER, """{"type":"GET_STATUS","requestId":1}""")
            try {
                while (elapsed() < timeoutMs) {
                    val read = try {
                        socket.inputStream.read(chunk)
                    } catch (e: SocketTimeoutException) {
                        0
                    }
                    if (read < 0) {
                        log("${elapsed()} ms the box closed the Cast connection")
                        break
                    }
                    for ((namespace, payload) in frames.add(chunk, read)) {
                        if (namespace == HEARTBEAT) {
                            if (payload.contains("\"PING\"")) send(HEARTBEAT, """{"type":"PONG"}""")
                            continue
                        }
                        if (namespace != RECEIVER) continue
                        log("${elapsed()} ms ${payload.take(160)}")
                        if (payload.contains("\"LAUNCH_ERROR\"")) return Result.FAILED
                        standbyOf(payload)?.let { standby = it }
                        if (launchedAt >= 0) {
                            if (standby == false) woke = true
                            session = session ?: sessionOf(payload, DEFAULT_RECEIVER)
                        }
                    }
                    if (launchedAt < 0) {
                        when (standby) {
                            false -> return Result.ALREADY_ON
                            true -> {
                                send(RECEIVER, """{"type":"LAUNCH","appId":"$DEFAULT_RECEIVER","requestId":2}""")
                                launchedAt = elapsed()
                                log("$launchedAt ms box in standby, launched the Cast receiver")
                            }
                            null -> Unit
                        }
                    }
                    val running = session
                    if (running != null && !stopped && (woke || elapsed() - launchedAt > holdMs)) {
                        send(RECEIVER, """{"type":"STOP","sessionId":"$running","requestId":3}""")
                        stopped = true
                        log("${elapsed()} ms stopped the Cast receiver")
                    }
                    if (woke && stopped) return Result.WOKE
                }
            } catch (e: IOException) {
                log("Cast connection lost: ${e.message}")
            }
            // Never leave the Cast screen up, whatever happened.
            session?.takeIf { !stopped }?.let { runCatching { send(RECEIVER, """{"type":"STOP","sessionId":"$it","requestId":3}""") } }
            return if (woke) Result.WOKE else Result.FAILED
        }
    }

    /** One CastMessage (protocol 0, string payload), behind its 4-byte big-endian length. */
    internal fun frame(namespace: String, json: String, source: String = "sender-0", destination: String = "receiver-0"): ByteArray {
        val body = ProtoWriter()
            .varint(1, 0)
            .string(2, source)
            .string(3, destination)
            .string(4, namespace)
            .varint(5, 0)
            .string(6, json)
            .toByteArray()
        return ByteBuffer.allocate(4 + body.size).putInt(body.size).put(body).array()
    }

    internal fun standbyOf(payload: String): Boolean? =
        Regex("\"isStandBy\"\\s*:\\s*(true|false)").find(payload)?.groupValues?.get(1)?.toBooleanStrict()

    /** The running session of [appId] from a RECEIVER_STATUS, if it's listed. */
    internal fun sessionOf(payload: String, appId: String): String? {
        val app = payload.indexOf("\"appId\":\"$appId\"").takeIf { it >= 0 } ?: return null
        return Regex("\"sessionId\"\\s*:\\s*\"([^\"]+)\"").find(payload, app)?.groupValues?.get(1)
    }

    /** Splits the byte stream into (namespace, payload) messages; reads that end mid-frame carry over. */
    internal class FrameReader {
        private val pending = ByteArrayOutputStream()

        fun add(data: ByteArray, length: Int): List<Pair<String, String>> {
            if (length > 0) pending.write(data, 0, length)
            val bytes = pending.toByteArray()
            val messages = mutableListOf<Pair<String, String>>()
            var at = 0
            while (bytes.size - at >= 4) {
                val size = ByteBuffer.wrap(bytes, at, 4).int
                if (size < 0 || size > 1 shl 20) throw IOException("bad Cast frame size $size")
                if (bytes.size - at - 4 < size) break
                val fields = ProtoFields.parse(bytes.copyOfRange(at + 4, at + 4 + size))
                messages += (fields.string(4).orEmpty()) to (fields.string(6).orEmpty())
                at += 4 + size
            }
            pending.reset()
            pending.write(bytes, at, bytes.size - at)
            return messages
        }
    }

    private fun open(host: String, port: Int, timeoutMs: Int): SSLSocket {
        val trustAny = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAny), null) }
        val plain = Socket()
        try {
            plain.connect(InetSocketAddress(host, port), timeoutMs)
            plain.soTimeout = timeoutMs
            plain.tcpNoDelay = true
            val socket = context.socketFactory.createSocket(plain, host, port, true) as SSLSocket
            socket.startHandshake()
            // Short reads from here on, so the loop can act on time (stopping the receiver) between messages.
            socket.soTimeout = 500
            return socket
        } catch (e: IOException) {
            plain.close()
            throw e
        }
    }
}
