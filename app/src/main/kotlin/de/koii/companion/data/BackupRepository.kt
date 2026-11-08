package de.koii.companion.data

import de.koii.audio.AudioConverter
import de.koii.audio.WavCodec
import de.koii.protocol.FileFlags
import de.koii.protocol.crc32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupSound(val slot: Int, val name: String, val file: String, val metaFile: String, val crc32: Long)

@Serializable
data class BackupProject(val name: String, val file: String, val size: Long, val crc32: Long)

/** meta.json of a backup zip. Deliberately without the device serial. */
@Serializable
data class BackupMeta(
    val version: Int = 1,
    val created: Long,
    val osVersion: String,
    val sounds: List<BackupSound> = emptyList(),
    val projects: List<BackupProject> = emptyList(),
)

class BackupReport(val done: Int, val skipped: List<String>)

/**
 * Backup = zip with meta.json first, then per sound `sounds/NNN.json` + `sounds/NNN.wav`, then `projects/Pxx.tar`.
 * The order lets a restore stream through the archive once without holding it in memory.
 */
class BackupRepository(private val session: DeviceSession, private val sounds: SoundRepository) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun backup(
        out: OutputStream,
        withSounds: Boolean,
        withProjects: Boolean,
        progress: (done: Long, total: Long) -> Unit,
    ): BackupReport = withContext(Dispatchers.IO) {
        val c = session.requireClient()
        val info = (session.link.value as? Link.Online)?.info
        val slotEntries = if (withSounds) c.list(session.soundsNodeId()).filter { !it.isDirectory } else emptyList()
        val projectEntries = if (withProjects) c.list(session.projectsNodeId()).filter { PROJECT_NAME.matches(it.name) } else emptyList()
        val total = (slotEntries.size + projectEntries.size).toLong()
        var done = 0L
        val skipped = ArrayList<String>()
        val soundMeta = ArrayList<BackupSound>()
        val projectMeta = ArrayList<BackupProject>()

        // Collect to a temp list first so meta.json can go first without buffering payloads.
        val tmp = java.io.File.createTempFile("koii-backup", ".zip")
        try {
            ZipOutputStream(tmp.outputStream().buffered()).use { z ->
                for (e in slotEntries) {
                    runCatching {
                        val (name, wav) = sounds.downloadAsWav(e.nodeId)
                        val meta = c.getMetadata(e.nodeId).toString()
                        val base = "sounds/%03d".format(e.nodeId)
                        z.putNextEntry(ZipEntry("$base.json")); z.write(meta.toByteArray()); z.closeEntry()
                        z.putNextEntry(ZipEntry("$base.wav")); z.write(wav); z.closeEntry()
                        soundMeta += BackupSound(e.nodeId, name, "$base.wav", "$base.json", crc32(wav))
                    }.onFailure { skipped += "sound ${e.nodeId}: ${it.message}" }
                    progress(++done, total)
                }
                for (p in projectEntries) {
                    runCatching {
                        val dl = c.download(p.nodeId)
                        if (!looksLikeTar(dl.bytes)) error("archive reads short or is not a valid tar (${dl.bytes.size} bytes)")
                        z.putNextEntry(ZipEntry("projects/${p.name}.tar")); z.write(dl.bytes); z.closeEntry()
                        projectMeta += BackupProject(p.name, "projects/${p.name}.tar", dl.bytes.size.toLong(), dl.crc32)
                    }.onFailure { skipped += "project ${p.name}: ${it.message}" }
                    progress(++done, total)
                }
            }
            val meta = BackupMeta(created = System.currentTimeMillis(), osVersion = info?.greet?.osVersion.orEmpty(),
                sounds = soundMeta, projects = projectMeta)
            ZipOutputStream(out.buffered()).use { z ->
                z.putNextEntry(ZipEntry("meta.json")); z.write(json.encodeToString(meta).toByteArray()); z.closeEntry()
                ZipInputStream(tmp.inputStream().buffered()).use { zin ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        z.putNextEntry(ZipEntry(entry.name)); zin.copyTo(z); z.closeEntry()
                        entry = zin.nextEntry
                    }
                }
            }
        } finally {
            tmp.delete()
        }
        BackupReport(soundMeta.size + projectMeta.size, skipped)
    }

    /** Reads only meta.json, so the UI can show what a restore would write. */
    suspend fun inspect(open: () -> InputStream): BackupMeta = withContext(Dispatchers.IO) {
        ZipInputStream(open().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (e.name == "meta.json") return@use json.decodeFromString<BackupMeta>(zin.readBytes().decodeToString())
                e = zin.nextEntry
            }
            throw IllegalArgumentException("Not a KO II backup: meta.json missing")
        }
    }

    /**
     * Streams through the archive and writes entries back. Existing slots/projects are only replaced when
     * [overwrite] is set; the UI must ask first.
     */
    suspend fun restore(
        open: () -> InputStream,
        meta: BackupMeta,
        overwrite: Boolean,
        withSounds: Boolean,
        withProjects: Boolean,
        progress: (done: Long, total: Long) -> Unit,
    ): BackupReport = withContext(Dispatchers.IO) {
        val c = session.requireClient()
        sounds.refresh()
        val projectsNode = session.projectsNodeId()
        val existingProjects = c.list(projectsNode).filter { PROJECT_NAME.matches(it.name) }.associateBy { it.name }
        val soundByFile = meta.sounds.associateBy { it.file }
        val projectByFile = meta.projects.associateBy { it.file }
        val total = ((if (withSounds) meta.sounds.size else 0) + (if (withProjects) meta.projects.size else 0)).toLong()
        var done = 0L
        var ok = 0
        val skipped = ArrayList<String>()
        var lastJson: JsonObject? = null

        ZipInputStream(open().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val name = e.name
                when {
                    name.endsWith(".json") && name.startsWith("sounds/") ->
                        lastJson = runCatching { json.parseToJsonElement(zin.readBytes().decodeToString()).jsonObject }.getOrNull()

                    name in soundByFile && withSounds -> {
                        val entry = soundByFile.getValue(name)
                        val wav = zin.readBytes()
                        try {
                            if (crc32(wav) != entry.crc32) error("checksum mismatch, archive damaged")
                            val taken = entry.slot in sounds.slots.value
                            if (taken && !overwrite) {
                                skipped += "slot ${entry.slot}: occupied"
                            } else {
                                if (taken) c.delete(entry.slot)
                                val prepared = AudioConverter.prepare(wav)
                                val id = c.upload(entry.name, prepared.pcm, session.soundsNodeId(), fileId = entry.slot)
                                c.setMetadata(id, (lastJson ?: JsonObject(emptyMap())).toString())
                                ok++
                            }
                        } catch (ex: Exception) {
                            skipped += "slot ${entry.slot}: ${ex.message}"
                        }
                        progress(++done, total)
                    }

                    name in projectByFile && withProjects -> {
                        val entry = projectByFile.getValue(name)
                        val tar = zin.readBytes()
                        try {
                            if (!looksLikeTar(tar)) error("archive reads short or is not a valid tar")
                            if (crc32(tar) != entry.crc32) error("checksum mismatch, archive damaged")
                            if (entry.name in existingProjects && !overwrite) {
                                skipped += "${entry.name}: exists on device"
                            } else {
                                // Kokoko waits 60 s for a project upload to finish.
                                c.upload("${entry.name}.tar", tar, projectsNode, flags = FileFlags.PROJECT_ARCHIVE, finalTimeoutMs = 60_000)
                                ok++
                            }
                        } catch (ex: Exception) {
                            skipped += "${entry.name}: ${ex.message}"
                        }
                        progress(++done, total)
                    }
                }
                e = zin.nextEntry
            }
        }
        sounds.refresh()
        session.refreshStorage()
        BackupReport(ok, skipped)
    }

    companion object {
        val PROJECT_NAME = Regex("P0[1-9]")

        /** A tar is a multiple of 512 bytes, at least a header plus end blocks, with a valid first-header checksum. */
        fun looksLikeTar(b: ByteArray): Boolean {
            if (b.size < 1024 || b.size % 512 != 0) return false
            val stored = String(b, 148, 8, Charsets.ISO_8859_1).trim { it == ' ' || it == '\u0000' }.toIntOrNull(8) ?: return false
            var sum = 0
            for (i in 0 until 512) sum += if (i in 148..155) 0x20 else (b[i].toInt() and 0xFF)
            return sum == stored
        }
    }
}
