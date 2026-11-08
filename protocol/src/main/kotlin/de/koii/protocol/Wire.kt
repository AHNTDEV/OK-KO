package de.koii.protocol

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/** 7-bit packing: every group of 7 payload bytes is preceded by one byte holding their high bits. */
object SevenBit {
    fun packedLength(n: Int): Int = n + (n + 6) / 7

    fun pack(src: ByteArray): ByteArray {
        if (src.isEmpty()) return ByteArray(0)
        val out = ByteArray(packedLength(src.size))
        var hdr = 0
        var o = 1
        for (c in src.indices) {
            val b = src[c].toInt() and 0xFF
            val d = c % 7
            out[hdr] = (out[hdr].toInt() or ((b shr 7) shl d)).toByte()
            out[o++] = (b and 0x7F).toByte()
            if (d == 6 && c < src.size - 1) {
                hdr += 8
                o++
            }
        }
        return out
    }

    fun unpack(src: ByteArray, from: Int = 0, to: Int = src.size): ByteArray {
        if (to <= from) return ByteArray(0)
        val out = ByteArrayOutputStream(to - from)
        var header = 0
        var bit = 0
        var c = from
        while (c < to) {
            if (c == from || bit > 6) {
                header = src[c].toInt()
                bit = 0
                c++
                continue
            }
            val high = if ((header and (1 shl bit)) != 0) 0x80 else 0
            out.write(high or (src[c].toInt() and 0x7F))
            bit++
            c++
        }
        return out.toByteArray()
    }
}

/** Max raw payload per PUT/GET chunk message for a device-reported [chunkSize]. */
fun maxChunkPayloadLength(chunkSize: Int): Int {
    val overhead = 8 + 2 + 1
    val m = chunkSize - 6
    if (m <= overhead) return 0
    val p = m - 1 - overhead
    return p - p / 8
}

fun crc32(data: ByteArray): Long = CRC32().also { it.update(data) }.value

/** Big-endian byte builder. */
class ByteWriter(capacity: Int = 32) {
    private val out = ByteArrayOutputStream(capacity)

    fun u8(v: Int) = apply { out.write(v and 0xFF) }
    fun u16(v: Int) = apply { out.write((v shr 8) and 0xFF); out.write(v and 0xFF) }
    fun u32(v: Long) = apply {
        out.write(((v shr 24) and 0xFF).toInt())
        out.write(((v shr 16) and 0xFF).toInt())
        out.write(((v shr 8) and 0xFF).toInt())
        out.write((v and 0xFF).toInt())
    }
    fun bytes(b: ByteArray) = apply { out.write(b, 0, b.size) }
    fun bytes(b: ByteArray, off: Int, len: Int) = apply { out.write(b, off, len) }
    fun zstring(s: String) = apply { out.write(s.toByteArray(Charsets.UTF_8)); out.write(0) }
    fun toByteArray(): ByteArray = out.toByteArray()
}

/** Big-endian byte reader over [data]; throws [KoiiProtocolException] when a field is truncated. */
class ByteReader(val data: ByteArray, var pos: Int = 0) {
    val remaining: Int get() = data.size - pos

    private fun need(n: Int) {
        if (remaining < n) throw KoiiProtocolException("Truncated payload: need $n bytes at $pos, have $remaining")
    }

    fun u8(): Int { need(1); return data[pos++].toInt() and 0xFF }
    fun u16(): Int { need(2); return (u8() shl 8) or u8() }
    fun u32(): Long { need(4); return (u16().toLong() shl 16) or u16().toLong() }
    fun skip(n: Int) { need(n); pos += n }
    fun rest(): ByteArray { val r = data.copyOfRange(pos, data.size); pos = data.size; return r }

    /** Reads up to the next 0 byte (consumed) or to the end. */
    fun zstring(): String {
        var end = pos
        while (end < data.size && data[end].toInt() != 0) end++
        val s = String(data, pos, end - pos, Charsets.UTF_8)
        pos = if (end < data.size) end + 1 else end
        return s
    }
}
