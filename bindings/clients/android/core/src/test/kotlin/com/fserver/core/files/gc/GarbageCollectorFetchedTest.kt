package com.fserver.core.files.gc

import android.content.ContextWrapper
import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.indexedFile
import com.fserver.core.support.sourceEntry
import com.fserver.core.support.sourceFiles
import com.fserver.core.sync.fileops.FileEvictor
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.files.FilesNode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.hours

/** A copy fetched on demand is evicted again once its TTL runs out - and only when the peer still holds it. */
class GarbageCollectorFetchedTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider(TestEpoch)
    private val storage = FakeStorage(clock = clock)

    private lateinit var node: FilesNode
    private lateinit var gc: GarbageCollector
    private lateinit var root: File
    private lateinit var file: File

    @Before
    fun setUp() {
        node = FilesNode.create(ContextWrapper(null), stagingDir = temp.newFolder("staging"))
        gc = GarbageCollector(storage, node, clock, TestScope(), FileEvictor(storage, sourceFiles(storage, node), LocalIndex(storage, node, clock).writer))
        root = temp.newFolder("source-root")
        file = File(root, FileName).apply { writeText("bytes") }
    }

    @Test
    fun `a fetched copy stays until its TTL runs out, then is evicted`() = runTest {
        setUpSource(SyncMode.Host)

        clock.advance(1.hours)
        gc.collectGarbage()
        assertTrue(file.exists())

        clock.advance(GarbageCollector.FetchedTtl)
        gc.collectGarbage()
        assertFalse(file.exists())
        assertTrue(storage.index.findFile(Key)?.state is LocalIndexedFile.State.Evicted)
    }

    @Test
    fun `an expired copy the peer does not confirm is kept`() = runTest {
        setUpSource(SyncMode.Offload(SyncMode.Offload.EvictPolicy.OlderThanDays(30)), remoteHash = "other")

        clock.advance(GarbageCollector.FetchedTtl + 1.hours)
        gc.collectGarbage()

        assertTrue(file.exists())
    }

    @Test
    fun `a pinned expired copy is kept`() = runTest {
        setUpSource(SyncMode.Host, pinned = true)

        clock.advance(GarbageCollector.FetchedTtl + 1.hours)
        gc.collectGarbage()

        assertTrue(file.exists())
    }

    @Test
    fun `a source that does not evict keeps its copies`() = runTest {
        setUpSource(SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.LastWriteWins))

        clock.advance(GarbageCollector.FetchedTtl + 1.hours)
        gc.collectGarbage()

        assertTrue(file.exists())
    }

    private suspend fun setUpSource(mode: SyncMode, remoteHash: String = Hash, pinned: Boolean = false) {
        storage.sources.upsert(
            sourceEntry(
                id = SourceId,
                deviceId = PeerId,
                location = SourceLocation.Directory(root.absolutePath),
                syncMode = mode,
                role = SourceEntry.Role.Initiator,
            )
        )
        storage.index.markProcessed(
            listOf(
                indexedFile(
                    sourceId = SourceId,
                    fileId = FileId,
                    path = FileName,
                    locator = file.absolutePath,
                    state = LocalIndexedFile.State.Present(pinned = pinned, fetchedAt = TestEpoch),
                    size = 5,
                ).copy(hash = ContentHash(Hash, "SHA-256"))
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
                    size = FileSize(5),
                    modifiedAt = TestEpoch,
                    hash = ContentHash(remoteHash, "SHA-256"),
                    seenAt = TestEpoch,
                )
            ),
        )
    }

    private companion object {
        const val SourceId = "source-1"
        const val PeerId = "device-peer"
        const val FileId = "file-1"
        const val FileName = "Report.txt"
        const val Hash = "h"
        val Key = IndexedFileKey(fileId = FileId, sourceId = SourceId)
    }
}
