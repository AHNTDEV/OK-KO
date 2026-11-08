package de.koii.companion.seq

import java.io.ByteArrayOutputStream

/** Standard MIDI file, format 0, 96 ticks per quarter (24 per step). */
object MidiFile {
    const val PPQ = 96
    const val TICKS_PER_STEP = PPQ / 4
    private const val GATE_TICKS = TICKS_PER_STEP / 2

    private class Ev(val tick: Int, val off: Boolean, val bytes: ByteArray)

    fun write(p: Pattern, channel: Int = 0): ByteArray {
        val ch = channel and 0x0F
        val events = ArrayList<Ev>()
        for (step in 0 until p.length) {
            for ((note, vel) in p.notesAt(step)) {
                val t = step * TICKS_PER_STEP
                events += Ev(t, false, byteArrayOf((0x90 or ch).toByte(), note.toByte(), vel.toByte()))
                events += Ev(t + GATE_TICKS, true, byteArrayOf((0x80 or ch).toByte(), note.toByte(), 0))
            }
        }
        // note-offs sort before note-ons on the same tick so repeated notes retrigger cleanly
        events.sortWith(compareBy({ it.tick }, { if (it.off) 0 else 1 }))

        val track = ByteArrayOutputStream()
        fun vlq(v: Int) {
            var value = v
            val buf = IntArray(5)
            var n = 0
            buf[n++] = value and 0x7F
            value = value shr 7
            while (value > 0) { buf[n++] = (value and 0x7F) or 0x80; value = value shr 7 }
            for (i in n - 1 downTo 0) track.write(buf[i])
        }
        // name, tempo, time signature
        val name = p.name.take(40).toByteArray(Charsets.ISO_8859_1)
        vlq(0); track.write(0xFF); track.write(0x03); vlq(name.size); track.write(name)
        val mpq = 60_000_000 / p.bpm.coerceIn(30, 300)
        vlq(0); track.write(0xFF); track.write(0x51); track.write(3)
        track.write((mpq shr 16) and 0xFF); track.write((mpq shr 8) and 0xFF); track.write(mpq and 0xFF)
        vlq(0); track.write(0xFF); track.write(0x58); track.write(4); track.write(4); track.write(2); track.write(24); track.write(8)

        var last = 0
        for (e in events) {
            vlq(e.tick - last); track.write(e.bytes); last = e.tick
        }
        val end = p.length * TICKS_PER_STEP
        vlq(maxOf(0, end - last)); track.write(0xFF); track.write(0x2F); track.write(0)

        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8); out.write(v and 0xFF) }
        fun u32(v: Int) { out.write(v ushr 24); out.write((v shr 16) and 0xFF); out.write((v shr 8) and 0xFF); out.write(v and 0xFF) }
        out.write("MThd".toByteArray()); u32(6); u16(0); u16(1); u16(PPQ)
        out.write("MTrk".toByteArray()); u32(track.size()); out.write(track.toByteArray())
        return out.toByteArray()
    }

    class Imported(val pattern: Pattern, val skippedNotes: Int, val truncated: Boolean)

    /** Reads note-ons of format 0/1 files onto the grid; notes outside C2–B5 (36–83) are skipped. */
    fun read(bytes: ByteArray, name: String = "Imported"): Imported {
        var pos = 0
        fun need(n: Int) { if (pos + n > bytes.size) throw IllegalArgumentException("MIDI file ends too early") }
        fun u8(): Int { need(1); return bytes[pos++].toInt() and 0xFF }
        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Int = (u16() shl 16) or u16()
        fun tag(): String { need(4); val s = String(bytes, pos, 4, Charsets.ISO_8859_1); pos += 4; return s }
        fun vlq(): Int { var v = 0; do { val b = u8(); v = (v shl 7) or (b and 0x7F); if (b and 0x80 == 0) return v } while (true) }

        if (tag() != "MThd") throw IllegalArgumentException("Not a MIDI file")
        val headLen = u32()
        u16() // format
        val tracks = u16()
        val division = u16()
        pos += maxOf(0, headLen - 6)
        if (division and 0x8000 != 0) throw IllegalArgumentException("SMPTE timing is not supported")
        val ppq = maxOf(1, division)

        val hits = ArrayList<Triple<Int, Int, Int>>() // tick, note, velocity
        var bpm = 120
        var tempoSet = false
        for (t in 0 until tracks) {
            if (pos >= bytes.size) break
            val id = tag()
            val len = u32()
            val end = minOf(bytes.size, pos + len)
            if (id != "MTrk") { pos = end; continue }
            var tick = 0
            var running = 0
            while (pos < end) {
                tick += vlq()
                var status = u8()
                if (status < 0x80) { pos--; status = running } else if (status < 0xF0) running = status
                when {
                    status == 0xFF -> {
                        val type = u8(); val l = vlq()
                        if (type == 0x51 && l == 3 && !tempoSet) {
                            val mpq = (u8() shl 16) or (u8() shl 8) or u8()
                            if (mpq > 0) { bpm = (60_000_000 / mpq).coerceIn(30, 300); tempoSet = true }
                        } else pos += l
                        if (type == 0x2F) pos = end
                    }
                    status == 0xF0 || status == 0xF7 -> pos += vlq()
                    else -> {
                        val kind = status and 0xF0
                        val d1 = u8()
                        val d2 = if (kind == 0xC0 || kind == 0xD0) 0 else u8()
                        if (kind == 0x90 && d2 > 0) hits += Triple(tick, d1, d2)
                    }
                }
            }
            pos = end
        }

        var p = Pattern.empty(name).withBpm(bpm)
        var skipped = 0
        var maxStep = 0
        var truncated = false
        for ((tick, note, vel) in hits) {
            val track = note - Pattern.FIRST_NOTE
            if (track !in 0 until Pattern.TRACKS) { skipped++; continue }
            val step = Math.round(tick.toDouble() * 4 / ppq).toInt()
            if (step >= Pattern.MAX_STEPS) { truncated = true; continue }
            p = p.withCell(track, step, if (vel >= 112) Pattern.VEL_ACCENT else Pattern.VEL_NORMAL)
            maxStep = maxOf(maxStep, step)
        }
        val needed = maxStep + 1
        p = p.withLength(Pattern.LENGTHS.firstOrNull { it >= needed } ?: Pattern.MAX_STEPS)
        return Imported(p, skipped, truncated)
    }
}
