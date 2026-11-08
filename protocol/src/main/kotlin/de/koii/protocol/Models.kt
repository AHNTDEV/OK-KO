package de.koii.protocol

/** Device-side failure (non-zero status) or a malformed reply. */
open class KoiiProtocolException(message: String, val status: Int? = null) : Exception(message)

class KoiiTimeoutException(message: String) : Exception(message)

object FileFlags {
    const val FILE = 1
    const val DIR = 2
    const val READ = 4
    const val WRITE = 8
    const val DELETE = 16
    const val MOVE = 32
    const val PLAYBACK = 64

    /** Observed on factory sounds (file | read | write | delete). Verified on hardware. */
    const val FACTORY_SOUND = FILE or READ or WRITE or DELETE

    /** Flags used for new uploads; falls back to [FACTORY_SOUND] if the device refuses (assumption). */
    const val USER_SOUND = FACTORY_SOUND or MOVE or PLAYBACK

    /** Project archives are uploaded to /projects with the READ capability. */
    const val PROJECT_ARCHIVE = FILE or READ
}

data class FileEntry(val nodeId: Int, val flags: Int, val size: Long, val name: String) {
    val isDirectory: Boolean get() = (flags and FileFlags.FILE) == 0
    val canMove: Boolean get() = (flags and FileFlags.MOVE) != 0
    val canPlay: Boolean get() = (flags and FileFlags.PLAYBACK) != 0
}

data class FileInfo(val nodeId: Int, val parentId: Int, val flags: Int, val size: Long, val name: String)

class Download(val nodeId: Int, val name: String, val flags: Int, val bytes: ByteArray) {
    /** CRC-32 (ISO-HDLC) of [bytes], computed on the host. */
    val crc32: Long by lazy { de.koii.protocol.crc32(bytes) }
}

sealed interface FileEvent {
    data class MetadataUpdated(val nodeId: Int, val json: String) : FileEvent
    data class Added(val nodeId: Int, val size: Long, val name: String) : FileEvent
    data class Updated(val nodeId: Int, val size: Long, val name: String) : FileEvent
    data class Deleted(val nodeId: Int) : FileEvent
    data class Moved(val oldNodeId: Int, val parentId: Int, val newNodeId: Int) : FileEvent
}

data class IdentityReply(val deviceId: Int, val sku: String)

data class GreetInfo(val fields: Map<String, String>) {
    val osVersion: String get() = fields["os_version"].orEmpty()
    val serial: String get() = fields["serial"].orEmpty()
    val sku: String get() = fields["sku"].orEmpty()
    val baseSku: String get() = fields["base_sku"].orEmpty()
    val mode: String get() = fields["mode"].orEmpty()
    val product: String get() = fields["product"].orEmpty()

    companion object {
        fun parse(text: String): GreetInfo {
            val map = LinkedHashMap<String, String>()
            for (pair in text.split(';')) {
                val i = pair.indexOf(':')
                if (i <= 0) continue
                map[pair.substring(0, i)] = pair.substring(i + 1)
            }
            return GreetInfo(map)
        }
    }
}

data class DeviceInfo(val identity: IdentityReply, val greet: GreetInfo, val chunkSize: Int) {
    /** K.O. II reports sku TE032AS002 with base_sku TE032AS001: always match on base_sku. */
    val isKoII: Boolean get() = (greet.baseSku.ifEmpty { greet.sku }) == "TE032AS001"
    fun meetsMinimumOs(min: String = MIN_OS): Boolean = VersionCompare.atLeast(greet.osVersion, min)

    companion object {
        const val MIN_OS = "2.0.5"
        const val USB_AUDIO_OS = "2.5.0"
    }
}

object VersionCompare {
    /** "0.x" is the beta channel and is always accepted, as in the reference tool. */
    fun atLeast(version: String, min: String): Boolean {
        val v = parts(version)
        if (v.isNotEmpty() && v[0] == 0) return true
        val m = parts(min)
        for (i in 0 until maxOf(v.size, m.size)) {
            val a = v.getOrElse(i) { 0 }
            val b = m.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return true
    }

    private fun parts(s: String): List<Int> =
        s.trim().split('.', '+', '-').mapNotNull { it.toIntOrNull() }
}
