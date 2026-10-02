package com.fserver.core.sync.fileops

import android.content.ContextWrapper
import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.preview.EvictionPreviewer
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.indexedFile
import com.fserver.core.support.sourceEntry
import com.fserver.core.support.sourceFiles
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.files.FilesNode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The host's previewer sees the bytes before they go, and never holds the eviction up. */
class FileEvictorPreviewTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider(TestEpoch)
    private val storage = FakeStorage(clock = clock)

    private lateinit var node: FilesNode
    private lateinit var file: File
    private lateinit var source: SourceEntry

    @Before
    fun setUp() {
        node = FilesNode.create(ContextWrapper(null), stagingDir = temp.newFolder("staging"))
        val root = temp.newFolder("source-root")
        file = File(root, FileName).apply { writeText(Content) }
        source = sourceEntry(
            id = SourceId,
            deviceId = PeerId,
            location = SourceLocation.Directory(root.absolutePath),
            syncMode = SyncMode.Offload(SyncMode.Offload.EvictPolicy.OlderThanDays(30)),
            role = SourceEntry.Role.Initiator,
        )
    }

    // Real time: the read hops to Dispatchers.IO, and virtual time would fire the timeout meanwhile.
    @Test
    fun `previewer reads the file before it is evicted`() = runBlocking {
        setUpIndex()
        var seen: String? = null
        val evictor = evictor { seen = it.read().use { s -> s.readBytes().decodeToString() } }

        assertTrue(evictor.evict(source, FileId, Hash))

        assertEquals(Content, seen)
        assertFalse(file.exists())
    }

    @Test
    fun `a failing previewer does not stop eviction`() = runTest {
        setUpIndex()

        assertTrue(evictor { error("decoder crashed") }.evict(source, FileId, Hash))
        assertFalse(file.exists())
    }

    @Test
    fun `a stuck previewer times out and eviction goes ahead`() = runTest {
        setUpIndex()

        assertTrue(evictor { awaitCancellation() }.evict(source, FileId, Hash))
        assertFalse(file.exists())
    }

    @Test
    fun `no preview is taken when eviction is refused`() = runTest {
        setUpIndex()
        var called = false

        assertFalse(evictor { called = true }.evict(source, FileId, ContentHash("other", Algorithm)))
        assertFalse(called)
        assertTrue(file.exists())
    }

    private fun evictor(previewer: EvictionPreviewer) =
        FileEvictor(storage, sourceFiles(storage, node), LocalIndex(storage, node, clock).writer, previewer)

    private suspend fun setUpIndex() {
        storage.sources.upsert(source)
        storage.index.markProcessed(
            listOf(
                indexedFile(
                    sourceId = SourceId,
                    fileId = FileId,
                    path = FileName,
                    locator = file.absolutePath,
                    state = LocalIndexedFile.State.Present(),
                    size = Content.length.toLong(),
                ).copy(hash = Hash)
            )
        )
        storage.remoteIndex.replace(
            sourceId = SourceId,
            deviceId = PeerId,
            files = listOf(
                RemoteIndexedFile(
                    sourceId = SourceId,
                    fileId = FileId,
                    path = FileName,
                    state = LocalIndexedFile.State.Present(),
                    size = FileSize(Content.length.toLong()),
                    modifiedAt = TestEpoch,
                    hash = Hash,
                    seenAt = TestEpoch,
                )
            ),
        )
    }

    private companion object {
        const val SourceId = "source-1"
        const val PeerId = "device-peer"
        const val FileId = "file-1"
        const val FileName = "photo.jpg"
        const val Content = "bytes"
        const val Algorithm = "SHA-256"
        val Hash = ContentHash("hash", Algorithm)
    }
}
