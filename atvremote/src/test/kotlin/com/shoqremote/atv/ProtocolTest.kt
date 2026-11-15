package com.shoqremote.atv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolTest {

    @Test
    fun varintMatchesProtobufEncoding() {
        val out = ByteArrayOutputStream()
        writeVarint(out, 300)
        assertContentEquals(byteArrayOf(0xAC.toByte(), 0x02), out.toByteArray())
    }

    @Test
    fun framesRoundTripBackToBack() {
        val out = ByteArrayOutputStream()
        val large = ByteArray(200) { it.toByte() }
        writeFrame(out, byteArrayOf(1, 2, 3))
        writeFrame(out, large)
        val input = ByteArrayInputStream(out.toByteArray())
        assertContentEquals(byteArrayOf(1, 2, 3), readFrame(input))
        assertContentEquals(large, readFrame(input))
        assertNull(readFrame(input))
    }

    @Test
    fun remoteConfigureEncodesAndParses() {
        val configure = assertNotNull(ProtoFields.parse(Remote.configure(615)).message(Remote.CONFIGURE))
        assertEquals(615, configure.int(1))
        val device = assertNotNull(configure.message(2))
        assertEquals(1, device.int(3))
        assertEquals("1", device.string(4))
        assertEquals("atvremote", device.string(5))
        assertEquals("1.0.0", device.string(6))
    }

    @Test
    fun pairingRequestCarriesVersionStatusAndNames() {
        val outer = ProtoFields.parse(Polo.pairingRequest("Shoq Remote (test)"))
        assertEquals(2, outer.int(1))
        assertEquals(200, outer.int(2))
        val request = assertNotNull(outer.message(10))
        assertEquals("atvremote", request.string(1))
        assertEquals("Shoq Remote (test)", request.string(2))
        val options = assertNotNull(ProtoFields.parse(Polo.options()).message(20))
        val encoding = assertNotNull(options.message(1))
        assertEquals(3, encoding.int(1))
        assertEquals(6, encoding.int(2))
        assertEquals(1, options.int(3))
    }

    @Test
    fun emptySubMessageStillCountsAsPresent() {
        val standby = ProtoWriter().bytes(Remote.START, ByteArray(0)).toByteArray()
        val start = assertNotNull(ProtoFields.parse(standby).message(Remote.START))
        assertNull(start.bool(1))
    }

    @Test
    fun textEditCarriesCountersAndValue() {
        val edit = assertNotNull(ProtoFields.parse(Remote.text(4, 9, "news")).message(Remote.IME_BATCH_EDIT))
        assertEquals(4, edit.int(1))
        assertEquals(9, edit.int(2))
        val value = assertNotNull(edit.message(3)?.message(2))
        assertEquals(4, value.int(1))
        assertEquals(4, value.int(2))
        assertEquals("news", value.string(3))
    }

    @Test
    fun voiceMessagesCarrySessionAndSamples() {
        val payload = assertNotNull(ProtoFields.parse(Remote.voicePayload(7, byteArrayOf(1, 2, 3))).message(31))
        assertEquals(7, payload.int(1))
        assertContentEquals(byteArrayOf(1, 2, 3), payload.bytes(2))
        assertEquals(7, ProtoFields.parse(Remote.voiceBegin(7)).message(Remote.VOICE_BEGIN)?.int(1))
        assertEquals(7, ProtoFields.parse(Remote.voiceEnd(7)).message(32)?.int(1))
    }

    @Test
    fun textMirrorDeletesOnlyWhatDiffers() {
        assertEquals(0 to "abc", TextMirror.diff("", "abc"))
        assertEquals(1 to "", TextMirror.diff("news", "new"))
        assertEquals(0 to " york", TextMirror.diff("new", "new york"))
        assertEquals(2 to "p", TextMirror.diff("hello", "help"))
        assertEquals(5 to "", TextMirror.diff("hello", ""))
        assertEquals(0 to "", TextMirror.diff("same", "same"))
        assertEquals(1 to "", TextMirror.diff("hi 😀", "hi "))
        assertEquals(1 to "😁", TextMirror.diff("😀", "😁"))
        assertEquals(0 to "é", TextMirror.diff("caf", "café"))
    }

    @Test
    fun selfSignedCertificateParsesAndVerifies() {
        val identity = ClientIdentity.generate("Shoq Remote test")
        identity.certificate.checkValidity()
        identity.certificate.verify(identity.certificate.publicKey)
        assertEquals(3, identity.certificate.version)
        assertTrue(identity.certificate.subjectX500Principal.name.contains("CN=Shoq Remote test"))
        val reloaded = ClientIdentity.load(identity.keyBytes, identity.certificateBytes)
        assertContentEquals(identity.certificateBytes, reloaded.certificateBytes)
    }

    @Test
    fun unsignedBytesDropsTheSignByte() {
        assertContentEquals(byteArrayOf(0x01, 0x00, 0x01), unsignedBytes(BigInteger.valueOf(65537)))
        assertContentEquals(byteArrayOf(0x80.toByte()), unsignedBytes(BigInteger.valueOf(128)))
    }

    @Test
    fun exactlyOneCheckByteFitsEachCode() {
        val client = ClientIdentity.generate("client").certificate
        val server = ClientIdentity.generate("server").certificate
        val accepted = (0..255).map { "%02X1A2B".format(it) }.filter { pairingSecret(client, server, it) != null }
        assertEquals(1, accepted.size)
        val secret = assertNotNull(pairingSecret(client, server, accepted.single().lowercase()))
        assertEquals(32, secret.size)
        assertEquals(accepted.single().substring(0, 2).toInt(16), secret[0].toInt() and 0xFF)
        assertNull(pairingSecret(client, server, "12345"))
        assertNull(pairingSecret(client, server, "GGGGGG"))
    }

    @Test
    fun pinAcceptsTheSameBoxWithANewCertificateOnly() {
        val trust = PinnedTrustManager("00".repeat(32), "54:AE:BC:9A:69:82")
        trust.checkServerTrusted(arrayOf(ClientIdentity.generate("atvremote/54:ae:bc:9a:69:82").certificate), "RSA")
        for (name in listOf("atvremote/54:AE:BC:9A:69:83", "xatvremote/54:AE:BC:9A:69:82", "atvremote/54:AE:BC:9A:69:82x")) {
            assertFailsWith<WrongDeviceException> { trust.checkServerTrusted(arrayOf(ClientIdentity.generate(name).certificate), "RSA") }
        }
        val pinnedOnly = PinnedTrustManager("00".repeat(32))
        assertFailsWith<WrongDeviceException> {
            pinnedOnly.checkServerTrusted(arrayOf(ClientIdentity.generate("atvremote/54:AE:BC:9A:69:82").certificate), "RSA")
        }
    }

    @Test
    fun wakeOnLanPacketIsSixFfThenTheMacSixteenTimes() {
        val packet = WakeOnLan.packet("54:ae:bc:9a:69:81")
        assertEquals(102, packet.size)
        assertTrue(packet.take(6).all { it == 0xFF.toByte() })
        val mac = byteArrayOf(0x54, 0xAE.toByte(), 0xBC.toByte(), 0x9A.toByte(), 0x69, 0x81.toByte())
        for (i in 0 until 16) assertContentEquals(mac, packet.copyOfRange(6 + i * 6, 12 + i * 6))
        assertFailsWith<IllegalArgumentException> { WakeOnLan.packet("54:ae:bc") }
    }

    @Test
    fun castFrameMatchesTheCastWireFormat() {
        val expected = "000000580800120873656e6465722d301a0a72656365697665722d30222875726e3a782d636173743a636f6d2e676f6f676c652e" +
            "636173742e74702e636f6e6e656374696f6e280032127b2274797065223a22434f4e4e454354227d"
        assertEquals(expected, CastWake.frame(CastWake.CONNECTION, """{"type":"CONNECT"}""").joinToString("") { "%02x".format(it) })
        // Two frames split at an awkward point still come out whole.
        val both = CastWake.frame(CastWake.RECEIVER, "a") + CastWake.frame(CastWake.HEARTBEAT, "b")
        val reader = CastWake.FrameReader()
        assertEquals(emptyList(), reader.add(both.copyOfRange(0, 7), 7))
        val rest = both.copyOfRange(7, both.size)
        assertEquals(listOf(CastWake.RECEIVER to "a", CastWake.HEARTBEAT to "b"), reader.add(rest, rest.size))
    }

    @Test
    fun castStatusReadsStandbyAndTheReceiverSession() {
        val status = """{"requestId":0,"status":{"applications":[{"appId":"2C6A6E3D","sessionId":"yt"},""" +
            """{"appId":"CC1AD845","namespaces":[{"name":"x"}],"sessionId":"abc-1"}],"isStandBy":true}}"""
        assertEquals(true, CastWake.standbyOf(status))
        assertEquals("abc-1", CastWake.sessionOf(status, CastWake.DEFAULT_RECEIVER))
        assertNull(CastWake.sessionOf("""{"status":{"applications":[{"appId":"2C6A6E3D","sessionId":"yt"}]}}""", CastWake.DEFAULT_RECEIVER))
        assertNull(CastWake.standbyOf("""{"status":{"volume":{}}}"""))
    }

    @Test
    fun castWakeLeavesABoxThatIsOnAlone() {
        val seen = fakeCastReceiver(standby = false, wakesOn = null) { port ->
            assertEquals(CastWake.Result.ALREADY_ON, CastWake.wake("127.0.0.1", port, 8_000))
        }
        assertEquals(listOf("CONNECT", "GET_STATUS"), seen)
    }

    @Test
    fun castWakeKeepsTheReceiverUpUntilTheBoxIsAwake() {
        val seen = fakeCastReceiver(standby = true, wakesOn = "LAUNCH") { port ->
            assertEquals(CastWake.Result.WOKE, CastWake.wake("127.0.0.1", port, 8_000))
        }
        assertEquals(listOf("CONNECT", "GET_STATUS", "LAUNCH", "STOP session-7"), seen)
    }

    @Test
    fun castWakeStopsTheReceiverAfterTheHoldWhenTheBoxWakesOnlyThen() {
        val seen = fakeCastReceiver(standby = true, wakesOn = "STOP") { port ->
            assertEquals(CastWake.Result.WOKE, CastWake.wake("127.0.0.1", port, 8_000, holdMs = 600))
        }
        assertEquals(listOf("CONNECT", "GET_STATUS", "LAUNCH", "STOP session-7"), seen)
    }

    @Test
    fun castWakeReportsFailureButStillClosesTheReceiver() {
        val seen = fakeCastReceiver(standby = true, wakesOn = null) { port ->
            assertEquals(CastWake.Result.FAILED, CastWake.wake("127.0.0.1", port, 2_500, holdMs = 600))
        }
        assertEquals(listOf("CONNECT", "GET_STATUS", "LAUNCH", "STOP session-7"), seen)
    }

    /**
     * A Cast receiver on localhost that answers like the box, waking when it gets [wakesOn] (LAUNCH or STOP; null
     * never wakes). Returns the requests it got, in order.
     */
    private fun fakeCastReceiver(standby: Boolean, wakesOn: String?, client: (Int) -> Unit): List<String> {
        val box = ClientIdentity.generate("fake cast")
        val password = "test".toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("box", box.privateKey, password, arrayOf(box.certificate))
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, password) }
        val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        val server = context.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        val done = thread(isDaemon = true) {
            runCatching {
                (server.accept() as SSLSocket).use { socket ->
                    val out = socket.outputStream
                    fun reply(json: String) = out.write(CastWake.frame(CastWake.RECEIVER, json, "receiver-0", "sender-0")).also { out.flush() }
                    val reader = CastWake.FrameReader()
                    val buffer = ByteArray(4096)
                    var asleep = standby
                    out.write(CastWake.frame(CastWake.HEARTBEAT, """{"type":"PING"}""", "receiver-0", "sender-0"))
                    while (true) {
                        val read = socket.inputStream.read(buffer)
                        if (read < 0) break
                        for ((namespace, payload) in reader.add(buffer, read)) {
                            if (namespace == CastWake.HEARTBEAT) continue
                            val type = Regex("\"type\":\"(\\w+)\"").find(payload)!!.groupValues[1]
                            when (type) {
                                "GET_STATUS" -> reply("""{"requestId":1,"status":{"isStandBy":$asleep},"type":"RECEIVER_STATUS"}""")
                                "LAUNCH" -> {
                                    reply("""{"launchRequestId":2,"status":"USER_ALLOWED","type":"LAUNCH_STATUS"}""")
                                    reply("""{"requestId":2,"status":{"applications":[{"appId":"CC1AD845","sessionId":"session-7"}]},"type":"RECEIVER_STATUS"}""")
                                    if (wakesOn == "LAUNCH") {
                                        Thread.sleep(300)
                                        asleep = false
                                        reply("""{"requestId":0,"status":{"isStandBy":false},"type":"RECEIVER_STATUS"}""")
                                    }
                                }
                                "STOP" -> {
                                    if (wakesOn == "STOP") asleep = false
                                    reply("""{"requestId":3,"status":{"isStandBy":$asleep},"type":"RECEIVER_STATUS"}""")
                                }
                            }
                            seen += if (type == "STOP") "STOP " + Regex("\"sessionId\":\"([^\"]+)\"").find(payload)!!.groupValues[1] else type
                        }
                    }
                }
            }
        }
        server.use {
            client(server.localPort)
        }
        done.join(5_000)
        return seen.toList()
    }

    @Test
    fun tlsPresentsTheClientCertificateAndEnforcesThePin() {
        val box = ClientIdentity.generate("fake box")
        val remote = ClientIdentity.generate("remote")
        val seenClient = CompletableFuture<X509Certificate>()
        val recordClient = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                chain?.firstOrNull()?.let { seenClient.complete(it) }
            }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val password = "test".toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("box", box.privateKey, password, arrayOf(box.certificate))
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, password) }
        val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, arrayOf(recordClient), null) }
        val server = context.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        server.needClientAuth = true
        thread(isDaemon = true) {
            repeat(2) {
                runCatching {
                    (server.accept() as SSLSocket).use { socket ->
                        socket.startHandshake()
                        socket.outputStream.write(42)
                        socket.outputStream.flush()
                    }
                }
            }
        }
        server.use {
            val pinned = PinnedTrustManager(sha256Hex(box.certificateBytes))
            openTls("127.0.0.1", server.localPort, remote, pinned, 5_000).use { socket ->
                assertEquals(42, socket.inputStream.read())
            }
            assertContentEquals(remote.certificateBytes, seenClient.get(5, TimeUnit.SECONDS).encoded)
            val refused = assertFailsWith<IOException> {
                openTls("127.0.0.1", server.localPort, remote, PinnedTrustManager("00".repeat(32)), 5_000)
            }
            assertNotNull(refused.causeOf<WrongDeviceException>())
        }
    }
}
