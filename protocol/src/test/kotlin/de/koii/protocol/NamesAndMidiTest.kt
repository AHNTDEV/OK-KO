package de.koii.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NamesTest {
    @Test fun stripsSlotPrefixAndExtension() = assertEquals("kick", Names.normalizeSlotName("012 Kick.wav"))
    @Test fun lowercases() = assertEquals("snare", Names.normalizeSlotName("SNARE"))
    @Test fun removesDiacritics() = assertEquals("cafe", Names.normalizeSlotName("Café.wav"))
    @Test fun replacesNonAscii() = assertEquals("?", Names.normalizeSlotName("日"))
    @Test fun removesIllegalChars() = assertEquals("ab", Names.normalizeSlotName("a/b\\\"".replace("\"", "")))

    @Test
    fun longNamesAreShortenedTo7Dot8() {
        val n = Names.normalizeSlotName("abcdefghijklmnopqrstuvwxyz.wav")
        assertEquals("abcdefg.stuvwxyz", n)
        assertEquals(16, n.length)
    }

    @Test fun keepsUnknownExtension() = assertEquals("kick.v2", Names.normalizeSlotName("kick.v2"))
}

class UsbMidiTest {
    private fun roundTrip(msg: ByteArray): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        val packets = UsbMidiCodec.encode(msg)
        UsbMidiDecoder { out += it }.feed(packets, packets.size)
        return out
    }

    @Test
    fun sysexOfAllLengthsRoundTrips() {
        for (n in 3..40) {
            val msg = ByteArray(n) { 0x10 }.also { it[0] = 0xF0.toByte(); it[n - 1] = 0xF7.toByte() }
            val r = roundTrip(msg)
            assertEquals(1, r.size, "n=$n")
            assertContentEquals(msg, r[0], "n=$n")
        }
    }

    @Test
    fun noteOnUsesCin9() {
        val p = UsbMidiCodec.encode(byteArrayOf(0x90.toByte(), 36, 100))
        assertContentEquals(byteArrayOf(0x09, 0x90.toByte(), 36, 100), p)
        assertContentEquals(byteArrayOf(0x90.toByte(), 36, 100), roundTrip(byteArrayOf(0x90.toByte(), 36, 100))[0])
    }

    @Test
    fun realtimeRoundTrips() {
        assertContentEquals(byteArrayOf(0xF8.toByte()), roundTrip(byteArrayOf(0xF8.toByte()))[0])
    }

    @Test
    fun decoderSplitsAcrossFeeds() {
        val msg = byteArrayOf(0xF0.toByte(), 1, 2, 3, 4, 5, 0xF7.toByte())
        val packets = UsbMidiCodec.encode(msg)
        val out = ArrayList<ByteArray>()
        val d = UsbMidiDecoder { out += it }
        d.feed(packets.copyOfRange(0, 4), 4)
        d.feed(packets.copyOfRange(4, packets.size), packets.size - 4)
        assertContentEquals(msg, out.single())
    }
}
