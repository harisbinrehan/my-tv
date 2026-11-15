package com.shoqremote.atv

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/** The device that answered presented a different certificate from the pinned Shoq box. */
class WrongDeviceException(val fingerprint: String) : CertificateException("not the Shoq box (certificate $fingerprint)")

/**
 * Trusts only the pinned certificate, or one naming the same box ([boxId], the MAC in its "atvremote/<MAC>"
 * common name), so a box whose remote service made itself a new certificate still connects. No pin: records only.
 */
class PinnedTrustManager(private val pin: String?, private val boxId: String? = null) : X509TrustManager {
    @Volatile
    var seen: X509Certificate? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("the device sent no certificate")
        seen = leaf
        val fingerprint = sha256Hex(leaf.encoded)
        if (pin == null || fingerprint.equals(pin, ignoreCase = true)) return
        if (boxId != null && commonNames(leaf).any { it.equals("atvremote/$boxId", ignoreCase = true) }) return
        throw WrongDeviceException(fingerprint)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("this side only makes client connections")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** Always offers this remote's certificate, whatever issuers the box asks for. */
private class FixedKeyManager(private val identity: ClientIdentity) : X509KeyManager {
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
    override fun getCertificateChain(alias: String?) = arrayOf(identity.certificate)
    override fun getPrivateKey(alias: String?): PrivateKey = identity.privateKey
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

    private companion object {
        const val ALIAS = "remote"
    }
}

fun openTls(host: String, port: Int, identity: ClientIdentity, trust: PinnedTrustManager, timeoutMs: Int): SSLSocket {
    val context = SSLContext.getInstance("TLS")
    context.init(arrayOf(FixedKeyManager(identity)), arrayOf(trust), null)
    val plain = Socket()
    try {
        plain.connect(InetSocketAddress(host, port), timeoutMs)
        plain.soTimeout = timeoutMs
        plain.tcpNoDelay = true
        val socket = context.socketFactory.createSocket(plain, host, port, true) as SSLSocket
        socket.startHandshake()
        return socket
    } catch (e: IOException) {
        plain.close()
        throw e
    }
}

/** CN values of the certificate's subject; javax.naming isn't on Android, so the RFC 2253 form is split by hand. */
fun commonNames(cert: X509Certificate): List<String> =
    cert.subjectX500Principal.getName(X500Principal.RFC2253)
        .split(Regex("(?<!\\\\)[,+]"))
        .map { it.trim() }
        .filter { it.startsWith("CN=", ignoreCase = true) }
        .map { it.substring(3) }

fun sha256Hex(data: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

inline fun <reified T : Throwable> Throwable.causeOf(): T? =
    generateSequence(this) { it.cause }.filterIsInstance<T>().firstOrNull()
