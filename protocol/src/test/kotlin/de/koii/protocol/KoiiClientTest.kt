package de.koii.protocol

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class KoiiClientTest {
    private fun withClient(block: suspend (KoiiClient, FakeDevice) -> Unit) = runBlocking {
        val dev = FakeDevice(chunkSize = 512)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val client = KoiiClient(dev, scope, requestTimeoutMs = 2_000)
        try { block(client, dev) } finally { client.close(); scope.cancel() }
    }

    @Test
    fun connectReadsIdentityGreetAndChunkSize() = withClient { c, _ ->
        val info = c.connect()
        assertEquals("TE032AS002", info.identity.sku)
        assertEquals("2.5.1", info.greet.osVersion)
        assertEquals(512, info.chunkSize)
        assertTrue(info.isKoII)
        assertTrue(info.meetsMinimumOs())
    }

    @Test
    fun resolveWalksNames() = withClient { c, _ ->
        c.connect()
        assertEquals(1000, c.resolve("/sounds"))
        assertFailsWith<KoiiProtocolException> { c.resolve("/nope") }
    }

    @Test
    fun uploadListDownloadRoundTrip() = withClient { c, dev ->
        c.connect()
        val sounds = c.resolve("/sounds")
        val data = Random(1).nextBytes(5000)
        val id = c.upload("kick", data, sounds)
        assertTrue(id > 0)

        val listed = c.list(sounds)
        assertEquals(1, listed.size)
        assertEquals(5000L, listed[0].size)

        val dl = c.download(id)
        assertContentEquals(data, dl.bytes)
        assertEquals(crc32(data), dl.crc32)
        assertEquals("kick", c.getMetadata(id)["name"]!!.jsonPrimitive.content)
        assertEquals(1, dev.nodes.values.count { it.parent == sounds })
    }

    @Test
    fun moveAndDelete() = withClient { c, _ ->
        c.connect()
        val sounds = c.resolve("/sounds")
        val id = c.upload("a", ByteArray(100), sounds)
        val r = c.move(id, sounds, 500)
        assertEquals(500, r.newId)
        assertEquals(listOf(500), c.list(sounds).map { it.nodeId })
        c.delete(500)
        assertTrue(c.list(sounds).isEmpty())
        assertFailsWith<KoiiProtocolException> { c.delete(500) }
    }

    @Test
    fun metadataSetGet() = withClient { c, _ ->
        c.connect()
        val id = c.upload("a", ByteArray(10), c.resolve("/sounds"))
        c.setMetadata(id, """{"sound.rootnote":60}""")
        assertEquals("60", c.getMetadata(id)["sound.rootnote"]!!.jsonPrimitive.content)
    }

    @Test
    fun timeoutIsReported() = runBlocking {
        val silent = object : MidiTransport {
            override val incoming = kotlinx.coroutines.flow.MutableSharedFlow<ByteArray>()
            override suspend fun send(message: ByteArray) {}
            override fun close() {}
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val c = KoiiClient(silent, scope, requestTimeoutMs = 100)
        assertFailsWith<KoiiTimeoutException> { c.identify() }
        c.close(); scope.cancel()
    }
}
