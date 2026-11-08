package de.koii.companion.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoopFile(val uri: Uri, val name: String, val folder: String, val size: Long)

/** Optional loop folder on the phone, picked with the system folder picker (no storage permission needed). */
class LoopRepository(private val context: Context, private val prefs: Prefs, private val scope: CoroutineScope) {
    private val _loops = MutableStateFlow<List<LoopFile>>(emptyList())
    val loops: StateFlow<List<LoopFile>> = _loops

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        if (prefs.loopDir.value != null) refresh()
    }

    fun setFolder(tree: Uri) {
        val old = prefs.loopDir.value?.let(Uri::parse)
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (old != null && old != tree) {
            runCatching { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        prefs.setLoopDir(tree.toString())
        refresh()
    }

    fun clearFolder() {
        prefs.loopDir.value?.let {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        prefs.setLoopDir(null)
        _loops.value = emptyList()
        _error.value = null
    }

    fun refresh() {
        val tree = prefs.loopDir.value?.let(Uri::parse) ?: return
        scope.launch {
            _scanning.value = true
            _error.value = null
            try {
                _loops.value = withContext(Dispatchers.IO) {
                    val rootId = DocumentsContract.getTreeDocumentId(tree)
                    val out = ArrayList<LoopFile>()
                    walk(tree, rootId, "", 0, out)
                    out.sortedWith(compareBy({ it.folder.lowercase() }, { it.name.lowercase() }))
                }
            } catch (e: Exception) {
                _error.value = "Loop folder: ${e.message ?: e.javaClass.simpleName}"
                _loops.value = emptyList()
            } finally {
                _scanning.value = false
            }
        }
    }

    private fun walk(tree: Uri, docId: String, path: String, depth: Int, out: MutableList<LoopFile>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
        )
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                val mime = c.getString(2).orEmpty()
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (depth < MAX_DEPTH) walk(tree, id, if (path.isEmpty()) name else "$path/$name", depth + 1, out)
                } else if (isAudio(name, mime)) {
                    out += LoopFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, path, size)
                }
            }
        }
    }

    companion object {
        const val MAX_DEPTH = 3
        private val EXT = setOf("wav", "wave", "aif", "aiff", "mp3", "flac", "ogg", "m4a", "aac", "opus")

        fun isAudio(name: String, mime: String): Boolean =
            mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in EXT
    }
}
