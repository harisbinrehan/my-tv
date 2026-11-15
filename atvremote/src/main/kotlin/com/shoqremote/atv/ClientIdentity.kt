package com.shoqremote.atv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** This remote's TLS identity; the box remembers the certificate once pairing succeeds. */
class ClientIdentity(val privateKey: PrivateKey, val certificate: X509Certificate) {

    val keyBytes: ByteArray get() = privateKey.encoded
    val certificateBytes: ByteArray get() = certificate.encoded

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun generate(commonName: String): ClientIdentity {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val der = selfSignedCertificate(
                commonName,
                pair.public.encoded,
                pair.private,
                Date(now - DAY_MS),
                Date(now + 20 * 365 * DAY_MS),
            )
            return ClientIdentity(pair.private, parseCertificate(der))
        }

        fun load(keyBytes: ByteArray, certificateBytes: ByteArray) = ClientIdentity(
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes)),
            parseCertificate(certificateBytes),
        )

        fun parseCertificate(der: ByteArray): X509Certificate =
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }
}

internal fun selfSignedCertificate(
    commonName: String,
    publicKeyInfo: ByteArray,
    key: PrivateKey,
    notBefore: Date,
    notAfter: Date,
): ByteArray {
    val algorithm = Der.sequence(Der.SHA256_WITH_RSA, Der.NULL)
    val name = Der.sequence(Der.set(Der.sequence(Der.COMMON_NAME, Der.utf8(commonName))))
    val tbs = Der.sequence(
        Der.tagged(0, Der.integer(BigInteger.valueOf(2))),
        Der.integer(BigInteger(63, SecureRandom()).add(BigInteger.ONE)),
        algorithm,
        name,
        Der.sequence(Der.time(notBefore), Der.time(notAfter)),
        name,
        publicKeyInfo,
    )
    val signature = Signature.getInstance("SHA256withRSA").run {
        initSign(key)
        update(tbs)
        sign()
    }
    return Der.sequence(tbs, algorithm, Der.bitString(signature))
}

/** Just enough DER to write one self-signed X.509 v3 certificate. */
internal object Der {
    val NULL = byteArrayOf(0x05, 0x00)
    val SHA256_WITH_RSA = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)
    val COMMON_NAME = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)

    fun sequence(vararg parts: ByteArray) = element(0x30, *parts)
    fun set(vararg parts: ByteArray) = element(0x31, *parts)
    fun tagged(number: Int, inner: ByteArray) = element(0xA0 or number, inner)
    fun integer(value: BigInteger) = element(0x02, value.toByteArray())
    fun utf8(text: String) = element(0x0C, text.toByteArray(Charsets.UTF_8))
    fun bitString(bits: ByteArray) = element(0x03, byteArrayOf(0), bits)

    /** RFC 5280: UTCTime through 2049, GeneralizedTime from 2050. */
    fun time(date: Date): ByteArray {
        val utc = TimeZone.getTimeZone("UTC")
        val year = Calendar.getInstance(utc).apply { time = date }.get(Calendar.YEAR)
        val (tag, pattern) = if (year < 2050) 0x17 to "yyMMddHHmmss'Z'" else 0x18 to "yyyyMMddHHmmss'Z'"
        val text = SimpleDateFormat(pattern, Locale.US).apply { timeZone = utc }.format(date)
        return element(tag, text.toByteArray(Charsets.US_ASCII))
    }

    private fun element(tag: Int, vararg parts: ByteArray): ByteArray {
        val content = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(tag)
        val n = content.size
        when {
            n < 0x80 -> out.write(n)
            n <= 0xFF -> {
                out.write(0x81)
                out.write(n)
            }
            n <= 0xFFFF -> {
                out.write(0x82)
                out.write(n shr 8)
                out.write(n and 0xFF)
            }
            else -> {
                out.write(0x83)
                out.write(n shr 16)
                out.write((n shr 8) and 0xFF)
                out.write(n and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }
}
