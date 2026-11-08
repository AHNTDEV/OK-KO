package de.koii.companion.ui

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.koii.audio.MediaDecoder
import de.koii.audio.PreparedSound
import de.koii.audio.WavCodec
import de.koii.companion.AppGraph
import de.koii.companion.KoiiApp
import de.koii.companion.data.BackupMeta
import de.koii.companion.data.BackupRepository
import de.koii.companion.data.LoopFile
import de.koii.companion.data.TrashItem
import de.koii.protocol.FileEntry
import de.koii.protocol.Names
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class Tab(val label: String) { PADS("Pads"), SOUNDS("Sounds"), KEYS("Keys"), REC("Rec"), LIBRARY("Library") }

sealed interface Sub {
    data object Device : Sub
    data object Settings : Sub
    data object Upload : Sub
    data object Trash : Sub
    data class PadAssign(val project: Int, val group: Char, val pad: Int) : Sub
    data class SoundDetail(val id: Int) : Sub
}

data class StagedUpload(
    val key: Int,
    val fileName: String,
    val displayName: String,
    val sound: PreparedSound?,
    val error: String?,
)

class DetailState(
    val id: Int,
    val meta: JsonObject? = null,
    val peaks: FloatArray? = null,
    val error: String? = null,
)

class KoiiViewModel(app: Application) : AndroidViewModel(app) {
    val g: AppGraph = (app as KoiiApp).graph
    private val resolver get() = getApplication<Application>().contentResolver

    // ---- navigation ---------------------------------------------------------------------
    private val _tab = MutableStateFlow(Tab.PADS)
    val tab: StateFlow<Tab> = _tab
    private val _sub = MutableStateFlow<Sub?>(null)
    val sub: StateFlow<Sub?> = _sub

    fun select(t: Tab) { _tab.value = t; _sub.value = null }
    fun open(s: Sub) { _sub.value = s }
    fun back(): Boolean { if (_sub.value == null) return false; _sub.value = null; return true }

    // ---- notices ------------------------------------------------------------------------
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    fun say(msg: String?) { _notice.value = msg }

    /** Runs a short device operation and reports failures as a notice. */
    fun op(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { say("$label: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    private fun queue(label: String, block: suspend ((Long, Long) -> Unit) -> Unit) = g.transfers.enqueue(label, block)

    // ---- file helpers -------------------------------------------------------------------
    private fun readBytes(uri: Uri): ByteArray =
        resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read $uri")

    private fun fileName(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "sample"

    // ---- sounds ---------------------------------------------------------------------------
    fun preview(id: Int) = op("Preview") { g.sounds.preview(id) }
    fun rename(id: Int, name: String) = op("Rename") { g.sounds.rename(id, name) }
    fun moveOrSwap(from: Int, to: Int) = op("Move") { g.sounds.moveOrSwap(from, to) }
    fun deleteToTrash(ids: List<Int>) = queue("Delete") { p -> g.sounds.deleteToTrash(ids, p) }
    fun loadNames(ids: Collection<Int>) = viewModelScope.launch { runCatching { g.sounds.loadNames(ids) } }

    fun downloadSound(id: Int, uri: Uri) = queue("Download") { p ->
        val (_, wav) = g.sounds.downloadAsWav(id, p)
        withContext(Dispatchers.IO) { resolver.openOutputStream(uri)!!.use { it.write(wav) } }
    }

    /** Bulk export of sounds as WAV files into a folder chosen with the folder picker. */
    fun exportSounds(ids: List<Int>, tree: Uri) = queue("Export") { p ->
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        ids.forEachIndexed { i, id ->
            val (name, wav) = g.sounds.downloadAsWav(id)
            withContext(Dispatchers.IO) {
                val doc = DocumentsContract.createDocument(resolver, parent, "audio/wav", "%03d %s.wav".format(id, name))
                    ?: error("Cannot create file in the chosen folder")
                resolver.openOutputStream(doc)!!.use { it.write(wav) }
            }
            p((i + 1).toLong(), ids.size.toLong())
        }
        say("Exported ${ids.size} sounds")
    }

    fun restore(item: TrashItem) = op("Restore") { g.sounds.restore(item) }
    fun purge(item: TrashItem) = g.trash.remove(item)
    fun emptyTrash() = g.trash.empty()

    // ---- sound detail -----------------------------------------------------------------------
    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> = _detail
    private var detailJob: Job? = null

    fun openDetail(id: Int) {
        open(Sub.SoundDetail(id))
        _detail.value = DetailState(id)
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            try {
                val c = g.session.requireClient()
                val meta = c.getMetadata(id)
                _detail.value = DetailState(id, meta = meta)
                val dl = c.download(id)
                val ch = (meta["channels"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 1
                val peaks = withContext(Dispatchers.Default) { Peaks.fromPcm16(dl.bytes, ch.coerceIn(1, 2), 240) }
                _detail.value = DetailState(id, meta = meta, peaks = peaks)
            } catch (e: Exception) {
                _detail.update { DetailState(id, it?.meta, it?.peaks, e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** Writes only the changed numeric metadata keys (see protocol validators). */
    fun saveSoundMeta(id: Int, changes: Map<String, Int>) = op("Save") {
        if (changes.isEmpty()) return@op
        val json = buildJsonObject { changes.forEach { (k, v) -> put(k, v) } }
        g.sounds.setMetadata(id, json)
        say("Saved")
    }

    // ---- pad bank -----------------------------------------------------------------------------
    val padProject = MutableStateFlow(1)
    val padGroup = MutableStateFlow(0)
    private val _bank = MutableStateFlow<Map<Int, Int?>>(emptyMap())
    /** Pad (1..12) → assigned sound node id of the shown project/group. */
    val bank: StateFlow<Map<Int, Int?>> = _bank
    private var bankJob: Job? = null

    fun loadBank() {
        bankJob?.cancel()
        _bank.value = emptyMap()
        val project = padProject.value
        val group = de.koii.companion.data.PadRepository.GROUPS[padGroup.value]
        bankJob = viewModelScope.launch {
            try {
                for (pad in 1..de.koii.companion.data.PadRepository.PADS) {
                    val id = runCatching { g.pads.assignment(project, group, pad) }.getOrNull()
                    _bank.update { it + (pad to id) }
                }
                g.sounds.loadNames(_bank.value.values.filterNotNull())
            } catch (_: Exception) {
                // device went away; the next load retries
            }
        }
    }

    fun assignSound(project: Int, group: Char, pad: Int, soundId: Int) = op("Assign") {
        g.pads.assign(project, group, pad, soundId)
        loadBank()
    }

    // ---- loops ------------------------------------------------------------------------------
    fun setLoopFolder(tree: Uri) = g.loops.setFolder(tree)
    fun toggleLoopPreview(loop: LoopFile) = g.localPlayer.toggle(loop.uri) { say(it) }

    /** Converts a loop from the phone, uploads it to the next free slot and puts it on the pad. */
    fun assignLoop(loop: LoopFile, project: Int, group: Char, pad: Int) {
        g.localPlayer.stop()
        queue("Loop → pad ${pad}") { p ->
            val sound = g.sounds.prepare(withContext(Dispatchers.IO) { readBytes(loop.uri) })
            val slot = g.sounds.freeSlots().firstOrNull() ?: error("No free slot")
            val id = g.sounds.upload(sound, loop.name, slot, p)
            g.pads.assign(project, group, pad, id)
            loadBank()
        }
    }

    // ---- upload staging ---------------------------------------------------------------
    private val _staged = MutableStateFlow<List<StagedUpload>>(emptyList())
    val staged: StateFlow<List<StagedUpload>> = _staged
    private var nextKey = 0

    fun stage(uris: List<Uri>) {
        open(Sub.Upload)
        for (uri in uris) viewModelScope.launch {
            val key = nextKey++
            val name = fileName(uri)
            val entry = try {
                val sound = g.sounds.prepare(withContext(Dispatchers.IO) { readBytes(uri) })
                StagedUpload(key, name, Names.normalizeSlotName(name), sound, null)
            } catch (e: Exception) {
                StagedUpload(key, name, name, null, e.message ?: e.javaClass.simpleName)
            }
            _staged.update { it + entry }
        }
    }

    fun unstage(key: Int) = _staged.update { l -> l.filterNot { it.key == key } }
    fun clearStaged() { _staged.value = emptyList() }

    /** Sends all valid staged files to consecutive free slots starting at [startSlot]. */
    fun sendStaged(startSlot: Int?) {
        val items = _staged.value.filter { it.sound != null }
        if (items.isEmpty()) return
        val free = g.sounds.freeSlots(startSlot ?: 1).iterator()
        val plan = items.map { it to (if (free.hasNext()) free.next() else null) }
        _staged.value = emptyList()
        _sub.value = null
        queue("Upload") { progress ->
            plan.forEachIndexed { i, (item, slot) ->
                if (slot == null) throw IllegalStateException("No free slot for ${item.fileName}")
                g.sounds.upload(item.sound!!, item.displayName, slot) { d, t -> progress(i * 1000L + if (t > 0) d * 1000 / t else 0, plan.size * 1000L) }
            }
        }
    }

    // ---- projects & backup ----------------------------------------------------------------
    private val _projects = MutableStateFlow<List<FileEntry>>(emptyList())
    val projects: StateFlow<List<FileEntry>> = _projects
    fun refreshProjects() = op("Projects") { _projects.value = g.pads.projects() }

    fun downloadProject(entry: FileEntry, uri: Uri) = queue("Project ${entry.name}") { p ->
        val tar = g.pads.downloadProject(entry, p)
        if (!BackupRepository.looksLikeTar(tar)) throw IllegalStateException("Archive reads short or is not a valid tar")
        withContext(Dispatchers.IO) { resolver.openOutputStream(uri)!!.use { it.write(tar) } }
    }

    fun backupTo(uri: Uri, sounds: Boolean, projects: Boolean) = queue("Backup") { p ->
        val out = resolver.openOutputStream(uri) ?: error("Cannot write backup")
        val report = g.backup.backup(out, sounds, projects, p)
        say("Backup: ${report.done} items" + if (report.skipped.isEmpty()) "" else "\nSkipped:\n" + report.skipped.joinToString("\n"))
    }

    private val _restorePlan = MutableStateFlow<Pair<Uri, BackupMeta>?>(null)
    val restorePlan: StateFlow<Pair<Uri, BackupMeta>?> = _restorePlan

    fun inspectBackup(uri: Uri) = op("Backup") {
        _restorePlan.value = uri to g.backup.inspect { resolver.openInputStream(uri) ?: error("Cannot read backup") }
    }

    fun cancelRestore() { _restorePlan.value = null }

    fun restoreBackup(overwrite: Boolean, sounds: Boolean, projects: Boolean) {
        val (uri, meta) = _restorePlan.value ?: return
        _restorePlan.value = null
        queue("Restore") { p ->
            val report = g.backup.restore({ resolver.openInputStream(uri) ?: error("Cannot read backup") }, meta, overwrite, sounds, projects, p)
            say("Restore: ${report.done} items written" + if (report.skipped.isEmpty()) "" else "\nSkipped:\n" + report.skipped.joinToString("\n"))
        }
    }

    // ---- recorder -------------------------------------------------------------------------
    fun startRecording(uri: Uri) {
        try {
            val pfd = resolver.openFileDescriptor(uri, "rw") ?: error("Cannot open target")
            val channel = java.io.FileOutputStream(pfd.fileDescriptor).channel
            g.recorder.start(channel, g.prefs.recordBits.value) { result ->
                runCatching { channel.close(); pfd.close() }
                lastRecording.value = uri
                result.onFailure { say("Recording: ${it.message}") }
            }
        } catch (e: Exception) {
            say("Recording: ${e.message}")
        }
    }

    val lastRecording = MutableStateFlow<Uri?>(null)

    /** Re-reads the recorded WAV and sends it to the next free slot (converted to 46875 Hz / s16). */
    fun recordingAsSample(uri: Uri) = queue("Recording → sample") { p ->
        val bytes = withContext(Dispatchers.IO) { readBytes(uri) }
        val sound = g.sounds.prepare(bytes)
        val slot = g.sounds.freeSlots().firstOrNull() ?: error("No free slot")
        g.sounds.upload(sound, "rec " + java.text.SimpleDateFormat("MMdd HHmm", java.util.Locale.ROOT).format(java.util.Date()), slot, p)
    }

    fun playFileToDevice(uri: Uri) = op("Play to device") {
        val pcm = withContext(Dispatchers.Default) {
            val bytes = withContext(Dispatchers.IO) { readBytes(uri) }
            when {
                WavCodec.isWav(bytes) -> WavCodec.decode(bytes)
                de.koii.audio.AiffCodec.isAiff(bytes) -> de.koii.audio.AiffCodec.decode(bytes)
                else -> MediaDecoder.decode(bytes)
            }
        }
        g.player.play(pcm) { err -> if (err != null) say("Play to device: ${err.message}") }
    }
}
