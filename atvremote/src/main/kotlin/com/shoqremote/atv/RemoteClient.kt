package com.shoqremote.atv

import java.io.IOException
import java.net.SocketException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** The box refused this remote's certificate: it was never paired, or the box has forgotten it. */
class NotPairedException(cause: Throwable?) : IOException("this remote is not paired with the box", cause)

/** [features] is the box's supported-feature bit mask from its configure message. */
data class DeviceInfo(val vendor: String, val model: String, val appVersion: String, val features: Int = 0)

/** Box events, delivered on the client's reader thread. */
interface RemoteListener {
    /** Every message from the box before it is handled, for diagnostics. */
    fun onRaw(frame: ByteArray) {}
    fun onReady(info: DeviceInfo) {}
    fun onPower(on: Boolean) {}
    fun onVolume(level: Int, max: Int, muted: Boolean) {}
    fun onCurrentApp(packageName: String) {}

    /** The TV asked for a keyboard: a text box took focus. Google TV boxes send this; this Shoq box hasn't been seen to. */
    fun onImeShow() {}
    fun onClosed(error: Throwable?) {}
}

/** One live connection to the box's remote port; make a new client to reconnect. */
class RemoteClient(
    private val host: String,
    private val identity: ClientIdentity,
    private val listener: RemoteListener,
    private val pin: String? = ShoqBox.CERT_SHA256,
    private val port: Int = ShoqBox.REMOTE_PORT,
    private val boxId: String? = ShoqBox.BOX_ID,
) : AutoCloseable {

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var closed = false
    @Volatile private var ready = false
    @Volatile private var imeCounter: Int? = null
    @Volatile private var fieldCounter: Int? = null
    @Volatile private var voiceBegin: CompletableFuture<Int>? = null
    private var features = Remote.FEATURES
    private var deviceInfo = DeviceInfo("", "", "")
    private val writeLock = Any()

    /** Throws WrongDeviceException, NotPairedException or a plain IOException when the box is unreachable. */
    fun connect() {
        val tls = try {
            openTls(host, port, identity, PinnedTrustManager(pin, boxId), timeoutMs = 6_000)
        } catch (e: IOException) {
            e.causeOf<WrongDeviceException>()?.let { throw it }
            if (e is SSLException) throw NotPairedException(e)
            throw e
        }
        tls.soTimeout = IDLE_TIMEOUT_MS
        socket = tls
        Thread({ readLoop(tls) }, "shoq-remote-reader").apply { isDaemon = true }.start()
    }

    fun sendKey(code: Int, direction: Int = Direction.SHORT) = send(Remote.key(code, direction))

    /** Opens an app by package name, or any deep link the box understands. */
    fun launch(appOrLink: String) =
        launchLink(if ("://" in appOrLink) appOrLink else "market://launch?id=$appOrLink")

    fun launchLink(link: String) = send(Remote.appLink(link))

    /** Types into whichever text field is focused on the TV. */
    fun sendText(text: String) {
        if (text.isNotEmpty()) send(Remote.text(imeCounter ?: 0, fieldCounter ?: 0, text))
    }

    /** Presses Search, which opens the box's voice input; returns the session to stream audio into. */
    fun startVoice(timeoutMs: Long = 2_000): Int {
        val pending = CompletableFuture<Int>()
        voiceBegin = pending
        try {
            sendKey(Keys.SEARCH)
            val session = try {
                pending.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                throw IOException("the box did not open voice input")
            }
            send(Remote.voiceBegin(session))
            return session
        } finally {
            voiceBegin = null
        }
    }

    /** Streams 16-bit mono PCM; the box wants pieces of 8 to 20 KiB, so short ones are padded with silence. */
    fun sendVoice(session: Int, pcm: ByteArray) {
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + VOICE_CHUNK_MAX, pcm.size)
            val piece = pcm.copyOfRange(offset, end)
            send(Remote.voicePayload(session, if (piece.size < VOICE_CHUNK_MIN) piece.copyOf(VOICE_CHUNK_MIN) else piece))
            offset = end
        }
    }

    fun endVoice(session: Int) = send(Remote.voiceEnd(session))

    override fun close() {
        closed = true
        runCatching { socket?.close() }
    }

    private fun send(message: ByteArray) {
        val current = socket ?: throw IOException("not connected")
        synchronized(writeLock) { writeFrame(current.outputStream, message) }
    }

    private fun readLoop(tls: SSLSocket) {
        var error: Throwable? = null
        try {
            val input = tls.inputStream.buffered()
            while (!closed) {
                val frame = readFrame(input) ?: break
                listener.onRaw(frame)
                handle(ProtoFields.parse(frame))
            }
            if (!closed && !ready) error = NotPairedException(null)
        } catch (t: Throwable) {
            if (!closed) {
                error = t.causeOf<WrongDeviceException>()
                    ?: if (!ready && (t is SSLException || t is SocketException)) NotPairedException(t) else t
            }
        } finally {
            runCatching { tls.close() }
            listener.onClosed(error)
        }
    }

    private fun handle(message: ProtoFields) {
        message.message(Remote.CONFIGURE)?.let { configure ->
            val supported = configure.int(1) ?: 0
            if (supported != 0) features = Remote.FEATURES and supported
            val device = configure.message(2)
            deviceInfo = DeviceInfo(device?.string(2).orEmpty(), device?.string(1).orEmpty(), device?.string(6).orEmpty(), supported)
            send(Remote.configure(features))
        }
        if (message.has(Remote.SET_ACTIVE)) {
            send(Remote.setActive(features))
            if (!ready) {
                ready = true
                listener.onReady(deviceInfo)
            }
        }
        message.message(Remote.PING_REQUEST)?.let { send(Remote.pingResponse(it.int(1) ?: 0)) }
        message.message(Remote.START)?.let { listener.onPower(it.bool(1) ?: false) }
        message.message(Remote.SET_VOLUME_LEVEL)?.let {
            listener.onVolume(it.int(7) ?: 0, it.int(6) ?: 0, it.bool(8) ?: false)
        }
        message.message(Remote.IME_KEY_INJECT)?.let { ime ->
            val app = ime.message(1)
            app?.int(1)?.let { imeCounter = it }
            ime.message(2)?.int(1)?.let { fieldCounter = it }
            app?.string(12)?.takeIf { it.isNotEmpty() }?.let(listener::onCurrentApp)
        }
        message.message(Remote.VOICE_BEGIN)?.let { begin -> voiceBegin?.complete(begin.int(1) ?: 0) }
        message.message(Remote.IME_SHOW_REQUEST)?.let { show ->
            show.message(2)?.int(1)?.let { fieldCounter = it }
            listener.onImeShow()
        }
        message.message(Remote.IME_BATCH_EDIT)?.let { edit ->
            if (imeCounter == null) edit.int(1)?.let { imeCounter = it }
            if (fieldCounter == null) edit.int(2)?.let { fieldCounter = it }
        }
    }

    private companion object {
        const val IDLE_TIMEOUT_MS = 16_000
        const val VOICE_CHUNK_MAX = 20 * 1024
        const val VOICE_CHUNK_MIN = 8 * 1024
    }
}
