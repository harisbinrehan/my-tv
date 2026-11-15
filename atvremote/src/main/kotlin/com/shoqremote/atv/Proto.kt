package com.shoqremote.atv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Builds one protobuf message from varint and length-delimited fields, the only kinds this protocol uses. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter = apply {
        writeVarint(out, field.toLong() shl 3)
        writeVarint(out, value)
    }

    fun varint(field: Int, value: Int): ProtoWriter = varint(field, value.toLong())

    fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        writeVarint(out, (field.toLong() shl 3) or 2L)
        writeVarint(out, value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, body: ProtoWriter.() -> Unit): ProtoWriter =
        bytes(field, ProtoWriter().apply(body).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()
}

/** A parsed protobuf message; a repeated scalar field resolves to its last value, as protobuf does. */
class ProtoFields private constructor(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int): Boolean = fields.containsKey(field)
    fun long(field: Int): Long? = fields[field]?.filterIsInstance<Long>()?.lastOrNull()
    fun int(field: Int): Int? = long(field)?.toInt()
    fun bool(field: Int): Boolean? = long(field)?.let { it != 0L }
    fun bytes(field: Int): ByteArray? = fields[field]?.filterIsInstance<ByteArray>()?.lastOrNull()
    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)
    fun message(field: Int): ProtoFields? = bytes(field)?.let(::parse)

    companion object {
        fun parse(data: ByteArray): ProtoFields {
            val fields = HashMap<Int, MutableList<Any>>()
            val cursor = Cursor(data)
            while (cursor.hasMore()) {
                val key = cursor.varint()
                val field = (key ushr 3).toInt()
                when ((key and 7L).toInt()) {
                    0 -> fields.getOrPut(field) { mutableListOf() }.add(cursor.varint())
                    1 -> cursor.skip(8)
                    2 -> fields.getOrPut(field) { mutableListOf() }.add(cursor.take(cursor.varint()))
                    5 -> cursor.skip(4)
                    else -> throw IllegalArgumentException("unsupported wire type in field $field")
                }
            }
            return ProtoFields(fields)
        }
    }

    private class Cursor(private val data: ByteArray) {
        private var pos = 0

        fun hasMore() = pos < data.size

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < data.size) { "truncated varint" }
                val b = data[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift < 64) { "varint too long" }
            }
        }

        fun take(length: Long): ByteArray {
            require(length >= 0 && pos + length <= data.size) { "truncated field" }
            val end = pos + length.toInt()
            return data.copyOfRange(pos, end).also { pos = end }
        }

        fun skip(count: Int) {
            require(pos + count <= data.size) { "truncated field" }
            pos += count
        }
    }
}

fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while (v and 0x7FL.inv() != 0L) {
        out.write(((v and 0x7F) or 0x80).toInt())
        v = v ushr 7
    }
    out.write(v.toInt())
}

/** Both ports frame every message with its byte length as a varint; one write keeps it in one TLS record. */
fun writeFrame(out: OutputStream, payload: ByteArray) {
    val frame = ByteArrayOutputStream(payload.size + 5)
    writeVarint(frame, payload.size.toLong())
    frame.write(payload)
    out.write(frame.toByteArray())
    out.flush()
}

/** Reads one frame; null when the stream ends cleanly between frames. */
fun readFrame(input: InputStream, maxSize: Int = 1 shl 20): ByteArray? {
    var length = 0L
    var shift = 0
    while (true) {
        val b = input.read()
        if (b < 0) {
            if (shift == 0) return null
            throw EOFException("stream ended inside a frame length")
        }
        length = length or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) break
        shift += 7
        if (shift >= 64) throw IllegalStateException("frame length too long")
    }
    if (length > maxSize) throw IllegalStateException("frame of $length bytes is too large")
    val data = ByteArray(length.toInt())
    var offset = 0
    while (offset < data.size) {
        val read = input.read(data, offset, data.size - offset)
        if (read < 0) throw EOFException("stream ended inside a frame")
        offset += read
    }
    return data
}
