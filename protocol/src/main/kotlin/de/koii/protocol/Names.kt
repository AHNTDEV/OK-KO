package de.koii.protocol

import java.text.Normalizer

object Names {
    private val leadingSlot = Regex("^\\d{3} ")
    private val audioExt = setOf("wav", "wave", "aif", "aiff", "aifc", "flac", "mp3", "ogg", "m4a", "aac", "opus", "pcm")
    private val diacritics = Regex("\\p{Mn}+")

    const val MAX_LEN = 16

    /** Slot display name: strip leading "NNN ", extension, diacritics; 7 + "." + 8 if longer than 16; lowercase. */
    fun normalizeSlotName(raw: String): String {
        var s = raw.replace(leadingSlot, "")
        val dot = s.lastIndexOf('.')
        if (dot > 0 && s.substring(dot + 1).lowercase() in audioExt) s = s.substring(0, dot)
        s = s.replace("/", "").trim()
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "")
        s = s.map { if (it.code < 128) it else '?' }.joinToString("")
        s = s.replace("\\", "").replace("\"", "")
        if (s.length > MAX_LEN) s = s.take(7) + "." + s.takeLast(8)
        return s.lowercase()
    }
}
