package com.shoqremote.cli

import com.shoqremote.atv.BadCodeException
import com.shoqremote.atv.CastWake
import com.shoqremote.atv.ClientIdentity
import com.shoqremote.atv.DeviceInfo
import com.shoqremote.atv.Direction
import com.shoqremote.atv.Keys
import com.shoqremote.atv.PairingSession
import com.shoqremote.atv.PinnedTrustManager
import com.shoqremote.atv.RemoteClient
import com.shoqremote.atv.RemoteListener
import com.shoqremote.atv.ShoqBox
import com.shoqremote.atv.TextMirror
import com.shoqremote.atv.openTls
import com.shoqremote.atv.sha256Hex
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private const val CLIENT_NAME = "Shoq Remote (Mac test)"

/** Mac test tool: checks the pin, pairs, reads status and presses keys on the real box. */
fun main(args: Array<String>) {
    try {
        when (args.firstOrNull()) {
            "pin" -> pin(args.getOrElse(1) { ShoqBox.DEFAULT_HOST })
            "castwake" -> println("RESULT " + CastWake.wake(args.getOrElse(1) { ShoqBox.DEFAULT_HOST }) { println("CAST $it") })
            "pair" -> pair(args.at(1), File(args.at(2)), File(args.at(3)))
            "status" -> connectAnd(args.at(1), File(args.at(2))) { Thread.sleep(3_000) }
            "key" -> connectAnd(args.at(1), File(args.at(2))) { client -> pressKeys(client, args.drop(3)) }
            "run" -> connectAnd(args.at(1), File(args.at(2))) { client -> runSteps(client, args.drop(3)) }
            "watch" -> connectAnd(args.at(1), File(args.at(2)), dumpMessages = true) { client -> runSteps(client, args.drop(3)) }
            "voice" -> connectAnd(args.at(1), File(args.at(2))) { client ->
                speak(client, File(args.at(3)), args.getOrNull(4)?.toInt() ?: 16_000)
            }
            else -> usage()
        }
    } catch (e: Exception) {
        println("FAILED ${e::class.simpleName}: ${e.message}")
        exitProcess(1)
    }
    exitProcess(0)
}

private fun Array<String>.at(index: Int): String = getOrNull(index) ?: usage()

private fun usage(): Nothing {
    System.err.println("usage: cli pin [host] | castwake [host] | pair <host> <stateDir> <codeFile> | status <host> <stateDir> | key <host> <stateDir> <KEY>...")
    exitProcess(2)
}

private fun pin(host: String) {
    val trust = PinnedTrustManager(null)
    runCatching { openTls(host, ShoqBox.PAIRING_PORT, ClientIdentity.generate("pin probe"), trust, 6_000).close() }
    val cert = trust.seen ?: throw IllegalStateException("no certificate from $host")
    val fingerprint = sha256Hex(cert.encoded)
    println("SHA256 $fingerprint")
    println("SUBJECT ${cert.subjectX500Principal.name}")
    println("MATCHES_PIN ${fingerprint == ShoqBox.CERT_SHA256}")
    println("MATCHES_BOX_ID ${runCatching { ShoqBox.trust().checkServerTrusted(arrayOf(cert), "RSA") }.isSuccess}")
}

private fun identity(dir: File): ClientIdentity {
    val key = File(dir, "client.key")
    val cert = File(dir, "client.crt")
    if (key.exists() && cert.exists()) return ClientIdentity.load(key.readBytes(), cert.readBytes())
    dir.mkdirs()
    return ClientIdentity.generate(CLIENT_NAME).also {
        key.writeBytes(it.keyBytes)
        cert.writeBytes(it.certificateBytes)
    }
}

private fun pair(host: String, dir: File, codeFile: File) {
    codeFile.delete()
    PairingSession.start(host, identity(dir), CLIENT_NAME).use { session ->
        println("CODE_ON_TV write the code to $codeFile")
        val deadline = System.currentTimeMillis() + 10 * 60_000
        while (System.currentTimeMillis() < deadline) {
            if (codeFile.exists()) {
                val code = codeFile.readText().trim()
                codeFile.delete()
                try {
                    session.finish(code)
                    println("PAIRED")
                    return
                } catch (e: BadCodeException) {
                    println(if (e.sent) "BAD_CODE_REJECTED_BY_BOX" else "BAD_CODE_CHECK_BYTE")
                    if (e.sent) throw e
                }
            }
            Thread.sleep(300)
        }
        throw IllegalStateException("no code within 10 minutes")
    }
}

private fun connectAnd(host: String, dir: File, dumpMessages: Boolean = false, action: (RemoteClient) -> Unit) {
    val ready = CountDownLatch(1)
    var failure: Throwable? = null
    val client = RemoteClient(host, identity(dir), object : RemoteListener {
        override fun onRaw(frame: ByteArray) {
            if (dumpMessages && !isPing(frame)) println("MSG " + describeMessage(frame))
        }
        override fun onReady(info: DeviceInfo) {
            println("READY vendor=${info.vendor} model=${info.model} version=${info.appVersion} features=${info.features}")
            ready.countDown()
        }
        override fun onPower(on: Boolean) = println("POWER ${if (on) "on" else "standby"}")
        override fun onVolume(level: Int, max: Int, muted: Boolean) = println("VOLUME $level/$max muted=$muted")
        override fun onCurrentApp(packageName: String) = println("APP $packageName")
        override fun onClosed(error: Throwable?) {
            failure = error
            ready.countDown()
        }
    })
    client.connect()
    try {
        if (!ready.await(10, TimeUnit.SECONDS)) throw IllegalStateException("no handshake from the box within 10 s")
        failure?.let { throw it }
        action(client)
        Thread.sleep(500)
    } finally {
        client.close()
    }
}

private fun pressKeys(client: RemoteClient, names: List<String>) {
    for (name in names) {
        client.sendKey(keyCode(name))
        println("SENT $name")
        Thread.sleep(700)
    }
}

/** Steps: key:NAME, long:NAME, app:PACKAGE, link:URI, wait:MS; APP lines show what the box has on screen. */
private fun runSteps(client: RemoteClient, steps: List<String>) {
    val mirror = TextMirror(client)
    for (step in steps) {
        val kind = step.substringBefore(':')
        val value = step.substringAfter(':', "")
        println("STEP $step")
        when (kind) {
            "key" -> client.sendKey(keyCode(value))
            "long" -> {
                val code = keyCode(value)
                client.sendKey(code, Direction.START_LONG)
                Thread.sleep(1_000)
                client.sendKey(code, Direction.END_LONG)
            }
            "app" -> client.launch(value)
            "link" -> client.launchLink(value)
            "text" -> client.sendText(value)
            "live" -> mirror.update(value)
            "clear" -> mirror.clearTyped()
            "wait" -> Thread.sleep(value.toLong())
            else -> throw IllegalArgumentException("unknown step $step")
        }
    }
}

/** Streams a raw 16-bit mono PCM file into the box's voice input at real-time pace. */
private fun speak(client: RemoteClient, pcm: File, sampleRate: Int) {
    val session = client.startVoice()
    println("VOICE_SESSION $session")
    val audio = pcm.readBytes()
    var offset = 0
    while (offset < audio.size) {
        val end = minOf(offset + 16 * 1024, audio.size)
        client.sendVoice(session, audio.copyOfRange(offset, end))
        Thread.sleep((end - offset) * 1_000L / (sampleRate * 2))
        offset = end
    }
    client.endVoice(session)
    println("VOICE_END")
    Thread.sleep(8_000)
}

private fun isPing(frame: ByteArray): Boolean = frame.isNotEmpty() && frame[0].toInt() == (8 shl 3 or 2)

/** Renders a protobuf message as field-number trees, guessing nested messages and printable strings. */
private fun describeMessage(data: ByteArray): String {
    val parts = mutableListOf<String>()
    var pos = 0
    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = data[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
    try {
        while (pos < data.size) {
            val key = varint()
            val field = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> parts += "$field=${varint()}"
                2 -> {
                    val length = varint().toInt()
                    val bytes = data.copyOfRange(pos, pos + length)
                    pos += length
                    val text = bytes.toString(Charsets.UTF_8)
                    parts += when {
                        bytes.isEmpty() -> "$field={}"
                        looksLikeMessage(bytes) -> "$field{${describeMessage(bytes)}}"
                        text.all { it == '\n' || it >= ' ' } -> "$field=\"$text\""
                        else -> "$field=<${bytes.size} bytes>"
                    }
                }
                1 -> pos += 8.also { parts += "$field=<fixed64>" }
                5 -> pos += 4.also { parts += "$field=<fixed32>" }
                else -> return parts.joinToString(" ") + " <bad wire type>"
            }
        }
    } catch (e: IndexOutOfBoundsException) {
        return parts.joinToString(" ") + " <truncated>"
    }
    return parts.joinToString(" ")
}

private fun looksLikeMessage(bytes: ByteArray): Boolean {
    var pos = 0
    fun varint(): Long? {
        var result = 0L
        var shift = 0
        while (pos < bytes.size && shift < 64) {
            val b = bytes[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        return null
    }
    while (pos < bytes.size) {
        val key = varint() ?: return false
        val field = (key ushr 3).toInt()
        if (field == 0 || field > 200) return false
        when ((key and 7).toInt()) {
            0 -> varint() ?: return false
            2 -> {
                val length = varint() ?: return false
                if (length < 0 || pos + length > bytes.size) return false
                pos += length.toInt()
            }
            1 -> pos += 8
            5 -> pos += 4
            else -> return false
        }
    }
    return pos == bytes.size
}

private fun keyCode(name: String): Int =
    Keys.byName[name.uppercase()] ?: name.toIntOrNull() ?: throw IllegalArgumentException("unknown key $name")
