package de.koii.companion.data

import de.koii.protocol.FileEntry
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Projects and pad assignment. A pad's sound is the metadata `{"sym": <sound node id>}` on the pad node. */
class PadRepository(private val session: DeviceSession) {
    companion object {
        val GROUPS = listOf('A', 'B', 'C', 'D')
        const val PADS = 12
        const val PROJECTS = 9
    }

    suspend fun projects(): List<FileEntry> =
        session.requireClient().list(session.projectsNodeId()).filter { BackupRepository.PROJECT_NAME.matches(it.name) }.sortedBy { it.name }

    private suspend fun padNode(project: Int, group: Char, pad: Int): Int =
        session.requireClient().resolve("/projects/P%02d/groups/%c/pads/%02d".format(project, group, pad))

    /** Sound node id assigned to the pad, or null if none. */
    suspend fun assignment(project: Int, group: Char, pad: Int): Int? {
        val meta = session.requireClient().getMetadata(padNode(project, group, pad))
        return (meta["sym"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
    }

    suspend fun assign(project: Int, group: Char, pad: Int, soundNode: Int) {
        val node = padNode(project, group, pad)
        session.requireClient().setMetadata(node, buildJsonObject { put("sym", soundNode) }.toString())
    }

    /**
     * Shows [project] and [group] on the device by writing `{"active": <node id>}` on the parent nodes.
     * Documented in the community protocol notes; not yet checked on a real K.O. II.
     */
    suspend fun showOnDevice(project: Int, group: Char) {
        val c = session.requireClient()
        val projectNode = c.resolve("/projects/P%02d".format(project))
        c.setMetadata(session.projectsNodeId(), buildJsonObject { put("active", projectNode) }.toString())
        val groupsNode = c.resolve("/projects/P%02d/groups".format(project))
        val groupNode = c.resolve("/projects/P%02d/groups/%c".format(project, group))
        c.setMetadata(groupsNode, buildJsonObject { put("active", groupNode) }.toString())
    }

    /** Raw .tar of a project (GET on its node). */
    suspend fun downloadProject(entry: FileEntry, progress: (Long, Long) -> Unit): ByteArray =
        session.requireClient().download(entry.nodeId, progress).bytes
}
