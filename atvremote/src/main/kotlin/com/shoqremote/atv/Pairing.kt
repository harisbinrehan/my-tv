package com.shoqremote.atv

import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingException(message: String) : IOException(message)

/** [sent] is false when the code's check byte caught the typo before anything went to the box. */
class BadCodeException(val sent: Boolean) : IOException("the code does not match the one on the TV")

/** One pairing attempt: [start] makes the box show a code, [finish] proves this remote saw it. */
class PairingSession private constructor(
    private val socket: SSLSocket,
    private val identity: ClientIdentity,
) : AutoCloseable {
    private val input = socket.inputStream.buffered()
    private val output = socket.outputStream
    private val serverCertificate = socket.session.peerCertificates.first() as X509Certificate

    fun finish(code: String) {
        val secret = pairingSecret(identity.certificate, serverCertificate, code) ?: throw BadCodeException(sent = false)
        send(Polo.secret(secret))
        expect(Polo.SECRET_ACK)
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private fun send(message: ByteArray) = writeFrame(output, message)

    private fun expect(field: Int) {
        val frame = readFrame(input) ?: throw PairingException("the box closed the pairing connection")
        val message = ProtoFields.parse(frame)
        when (val status = message.int(2) ?: 0) {
            Polo.STATUS_OK -> if (!message.has(field)) throw PairingException("unexpected pairing reply from the box")
            Polo.STATUS_BAD_SECRET -> throw BadCodeException(sent = true)
            else -> throw PairingException("the box refused pairing (status $status)")
        }
    }

    companion object {
        fun start(
            host: String,
            identity: ClientIdentity,
            clientName: String,
            pin: String? = ShoqBox.CERT_SHA256,
            port: Int = ShoqBox.PAIRING_PORT,
            boxId: String? = ShoqBox.BOX_ID,
        ): PairingSession {
            val socket = openTls(host, port, identity, PinnedTrustManager(pin, boxId), timeoutMs = 10_000)
            val session = PairingSession(socket, identity)
            try {
                session.send(Polo.pairingRequest(clientName))
                session.expect(Polo.PAIRING_REQUEST_ACK)
                session.send(Polo.options())
                session.expect(Polo.OPTIONS)
                session.send(Polo.configuration())
                session.expect(Polo.CONFIGURATION_ACK)
            } catch (e: Exception) {
                session.close()
                throw e
            }
            return session
        }
    }
}

/** SHA-256 of both public keys and the code's last four hex digits; its first byte must equal the code's first two. */
fun pairingSecret(client: X509Certificate, server: X509Certificate, code: String): ByteArray? {
    val hex = code.trim().uppercase()
    if (hex.length != 6 || hex.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
    val clientKey = client.publicKey as? RSAPublicKey ?: return null
    val serverKey = server.publicKey as? RSAPublicKey ?: return null
    val digest = MessageDigest.getInstance("SHA-256").run {
        update(unsignedBytes(clientKey.modulus))
        update(unsignedBytes(clientKey.publicExponent))
        update(unsignedBytes(serverKey.modulus))
        update(unsignedBytes(serverKey.publicExponent))
        update(hex.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        digest()
    }
    return digest.takeIf { (it[0].toInt() and 0xFF) == hex.substring(0, 2).toInt(16) }
}

/** Big-endian magnitude without Java's sign byte, which is how the box hashes the keys. */
internal fun unsignedBytes(value: BigInteger): ByteArray {
    val bytes = value.toByteArray()
    return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
}
