package de.koii.protocol

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WireTest {
    @Test
    fun packedLengthMatchesFormula() {
        assertEquals(0, SevenBit.packedLength(0))
        assertEquals(2, SevenBit.packedLength(1))
        assertEquals(8, SevenBit.packedLength(7))
        assertEquals(10, SevenBit.packedLength(8))
    }

    @Test
    fun packKnownVector() {
        // 0x80 -> high bit 0 of header set, data byte 0x00
        assertContentEquals(byteArrayOf(0x01, 0x00), SevenBit.pack(byteArrayOf(0x80.toByte())))
        assertContentEquals(byteArrayOf(0x00, 0x7F), SevenBit.pack(byteArrayOf(0x7F)))
        assertEquals(0, SevenBit.pack(ByteArray(0)).size)
    }

    @Test
    fun packOutputIsMidiSafe() {
        val data = ByteArray(1000) { it.toByte() }
        assertTrue(SevenBit.pack(data).all { it.toInt() in 0..127 })
    }

    @Test
    fun packUnpackRoundTrip() {
        val rnd = Random(42)
        for (n in listOf(0, 1, 6, 7, 8, 13, 14, 15, 100, 1023)) {
            val data = rnd.nextBytes(n)
            assertContentEquals(data, SevenBit.unpack(SevenBit.pack(data)), "n=$n")
        }
    }

    @Test
    fun chunkFormula() {
        assertEquals(0, maxChunkPayloadLength(17))
        // chunk 512: m=506, p=494, 494 - 61 = 433
        assertEquals(433, maxChunkPayloadLength(512))
    }

    @Test
    fun crcKnownVector() {
        assertEquals(0xCBF43926L, crc32("123456789".toByteArray()))
    }

    @Test
    fun requestFrameLayout() {
        val bytes = Frame.encodeRequest(0x2A, 0x0185, Te.CMD_GREET, ByteArray(0))
        assertEquals(10, bytes.size)
        assertEquals(0xF0, bytes[0].toInt() and 0xFF)
        assertEquals(0x2A, bytes[4].toInt())
        assertEquals(0x40, bytes[5].toInt())
        assertEquals(0x40 or 0x20 or (0x0185 shr 7), bytes[6].toInt())
        assertEquals(0x0185 and 127, bytes[7].toInt())
        assertEquals(1, bytes[8].toInt())
        assertEquals(0xF7, bytes[9].toInt() and 0xFF)
    }

    @Test
    fun responseParseRoundTrip() {
        val payload = byteArrayOf(1, 2, 0x80.toByte(), 0xFF.toByte())
        val raw = Frame.encodeResponse(7, 4000, 5, 0, payload)
        val f = assertNotNull(Frame.parse(raw))
        assertEquals(FrameKind.RESPONSE, f.kind)
        assertEquals(4000, f.requestId)
        assertEquals(0, f.status)
        assertContentEquals(payload, f.payload)
    }

    @Test
    fun logFrameIsRecognised() {
        val text = "hello".toByteArray()
        val raw = byteArrayOf(0xF0.toByte(), 0x00, 0x20, 0x76, 0x2A, 0x33) + SevenBit.pack(text) + byteArrayOf(0xF7.toByte())
        val f = assertNotNull(Frame.parse(raw))
        assertEquals(FrameKind.DEVICE_LOG, f.kind)
        assertEquals("hello", String(f.payload))
    }

    @Test
    fun foreignFramesAreIgnored() {
        assertNull(Frame.parse(byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte())))
    }

    @Test
    fun identityReplyDecodesSku() {
        val raw = byteArrayOf(
            0xF0.toByte(), 0x7E, 0x2A, 0x06, 0x02, 0x00, 0x20, 0x76,
            32, 0, 2, 0, 1, 0, 0, 0, 0xF7.toByte(),
        )
        val r = assertNotNull(Identity.parseReply(raw))
        assertEquals(0x2A, r.deviceId)
        assertEquals("TE032AS002", r.sku)
    }

    @Test
    fun greetParsing() {
        val g = GreetInfo.parse("chip_id:abc;mode:normal;os_version:2.5.1;serial:XYZ;sku:TE032AS002;base_sku:TE032AS001;")
        assertEquals("2.5.1", g.osVersion)
        assertEquals("XYZ", g.serial)
        assertTrue(DeviceInfo(IdentityReply(1, "TE032AS002"), g, 512).isKoII)
    }

    @Test
    fun versionComparison() {
        assertTrue(VersionCompare.atLeast("2.5.1", "2.0.5"))
        assertTrue(VersionCompare.atLeast("2.0.5", "2.0.5"))
        assertTrue(!VersionCompare.atLeast("2.0.4", "2.0.5"))
        assertTrue(!VersionCompare.atLeast("1.9.9", "2.0.5"))
        assertTrue(VersionCompare.atLeast("0.100.38", "2.0.5")) // beta channel
    }

    @Test
    fun eventParsing() {
        val deleted = FileMsg.parseEvent(byteArrayOf(10, 0x03, 0xE8.toByte()))
        assertEquals(FileEvent.Deleted(1000), deleted)
        val moved = FileMsg.parseEvent(byteArrayOf(13, 0, 5, 0x03, 0xE8.toByte(), 0, 9))
        assertEquals(FileEvent.Moved(5, 1000, 9), moved)
        val added = FileMsg.parseEvent(byteArrayOf(8, 0, 7, 0, 0, 0, 0, 1, 0, 'a'.code.toByte(), 0))
        assertEquals(FileEvent.Added(7, 256, "a"), added)
        assertNull(FileMsg.parseEvent(byteArrayOf(99)))
    }
}
