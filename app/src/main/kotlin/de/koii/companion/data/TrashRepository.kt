package de.koii.companion.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class TrashItem(
    val id: String,
    val slot: Int,
    val name: String,
    val sizeBytes: Long,
    val deletedAt: Long,
    /** Sound metadata JSON as read from the device before deletion. */
    val metadata: String,
)

/** App-side recycle bin: deleting a sound on the device first stores its PCM and metadata here. */
class TrashRepository(context: Context) {
    private val dir = File(context.filesDir, "trash").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<TrashItem>> = _items

    private fun load(): List<TrashItem> = runCatching {
        json.decodeFromString<List<TrashItem>>(index.readText())
    }.getOrDefault(emptyList()).filter { pcmFile(it.id).exists() }

    private fun save(list: List<TrashItem>) {
        index.writeText(json.encodeToString(list))
        _items.value = list
    }

    private fun pcmFile(id: String) = File(dir, "$id.pcm")

    @Synchronized
    fun add(slot: Int, name: String, pcm: ByteArray, metadata: String): TrashItem {
        val item = TrashItem(UUID.randomUUID().toString(), slot, name, pcm.size.toLong(), System.currentTimeMillis(), metadata)
        pcmFile(item.id).writeBytes(pcm)
        save(_items.value + item)
        return item
    }

    fun read(item: TrashItem): ByteArray = pcmFile(item.id).readBytes()

    @Synchronized
    fun remove(item: TrashItem) {
        pcmFile(item.id).delete()
        save(_items.value.filterNot { it.id == item.id })
    }

    @Synchronized
    fun empty() {
        _items.value.forEach { pcmFile(it.id).delete() }
        save(emptyList())
    }
}
