package com.fserver.core.sync.runner.action

import android.content.ContextWrapper
import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.scan.toFiles
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.conflict.ConflictCopier
import com.fserver.core.sync.conflict.ConflictDecision
import com.fserver.core.sync.conflict.ConflictResolver
import com.fserver.core.sync.conflict.seenVersion
import com.fserver.core.sync.fileops.FileDeleter
import com.fserver.core.sync.fileops.FileEvictor
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.index.toIndexed
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.remote.PeerFileOperations
import com.fserver.core.sync.transfer.FileDownloader
import com.fserver.core.sync.transfer.SourceUploader
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion
import com.fserver.files.upload.VersionVector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Actions that contradict the records they carry, or the index by now, never touch bytes. */
class FileActionRunnerGuardTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(localDeviceId = LocalId, clock = clock)
    private val node = FilesNode.create(ContextWrapper(null))
    private val uploader = mockk<SourceUploader>(relaxed = true)
    private val downloader = mockk<FileDownloader>(relaxed = true)

    private val writer = LocalIndex(storage, node, clock).writer
    private val peerFiles = mockk<PeerFileOperations>(relaxed = true)
    private val steps = ActionSteps(
        connector = mockk(relaxed = true),
        indexWriter = writer,
        peerFiles = peerFiles,
        sourceUploader = uploader,
        fileDownloader = downloader,
        fileDeleter = FileDeleter(storage, node, writer),
    )

    private val runner = FileActionRunner(
        steps = steps,
        conflicts = ConflictResolver(storage, steps, ConflictCopier(storage, node)),
        localHasher = mockk(relaxed = true),
        peerFiles = peerFiles,
        fileEvictor = FileEvictor(storage, node, writer),
        fileMover = mockk(relaxed = true),
    )

    private lateinit var root: File
    private lateinit var file: File
    private lateinit var locator: String

    @Before
    fun setUp() = runTest {
        root = temp.newFolder("source-root")
        file = File(root, "Report.txt").apply { writeText("mine") }
        locator = node.openSource(source().location.toFiles()).scan().result().getOrThrow().single().locator
        coEvery { downloader.download(any(), any(), any(), any()) } returns Unit
    }

    @Test
    fun `an evicted file is never uploaded`() = runTest {
        runner.execute(source(), FileAction.Upload(local(state = Evicted), null, "test")).getOrThrow()

        coVerify(exactly = 0) { uploader.uploadFile(any(), any(), any(), any()) }
    }

    @Test
    fun `an evicted side never wins last write wins`() = runTest {
        val conflict = FileAction.Conflict(
            local = local(state = Evicted, version = version(LocalId, hlc = 99)),
            remote = remote(version = version(PeerId, hlc = 1)),
            reason = "test",
        )

        // Adopting the merged version afterwards needs a live peer; only the direction matters here.
        runner.execute(source(), conflict)

        coVerify(exactly = 1) { downloader.download(any(), any(), any(), any()) }
    }

    @Test
    fun `a decision for a side evicted since is dropped`() = runTest {
        val source = source(SyncMode.Mirror.ConflictResolution.Ask)
        val conflict = FileAction.Conflict(
            local = local(state = Evicted, version = version(LocalId, hlc = 10)),
            remote = remote(version = version(PeerId, hlc = 20)),
            reason = "test",
        )
        storage.conflictDecisions.put(
            ConflictDecision(
                sourceId = source.id,
                fileId = FileIdValue,
                choice = ConflictDecision.Choice.KeepLocal,
                local = conflict.local.seenVersion(),
                remote = conflict.remote.seenVersion(),
                decidedAt = TestEpoch,
            )
        )

        runner.execute(source, conflict).getOrThrow()

        coVerify(exactly = 0) { uploader.uploadFile(any(), any(), any(), any()) }
        assertNull(storage.conflictDecisions.find(Key))
    }

    @Test
    fun `eviction waits for the peer to confirm a copy`() = runTest {
        index(local())

        runner.execute(source(), FileAction.EvictLocal(local(), "test")).getOrThrow()

        assertTrue(file.exists())
        assertTrue(storage.index.findFile(Key)?.state is LocalIndexedFile.State.Present)
    }

    @Test
    fun `eviction with a confirmed copy drops the bytes`() = runTest {
        index(local())
        confirmCopy(hash = "mine")

        runner.execute(source(), FileAction.EvictLocal(local(), "test")).getOrThrow()

        assertTrue(!file.exists())
        assertTrue(storage.index.findFile(Key)?.state is LocalIndexedFile.State.Evicted)
    }

    @Test
    fun `a file edited since planning is not evicted`() = runTest {
        index(local(hash = "edited"))
        confirmCopy(hash = "mine")

        runner.execute(source(), FileAction.EvictLocal(local(), "test")).getOrThrow()

        assertTrue(file.exists())
    }

    @Test
    fun `a pinned file is not evicted`() = runTest {
        val pinned = local(state = FileRecord.State.Present(pinned = true))
        index(pinned)
        confirmCopy(hash = "mine")

        runner.execute(source(), FileAction.EvictLocal(pinned, "test")).getOrThrow()

        assertTrue(file.exists())
    }

    @Test
    fun `a local deletion over a version the plan never saw is refused`() = runTest {
        index(local(version = version(LocalId, hlc = 20, count = 2)))

        runner.execute(
            source(),
            FileAction.DeleteLocal(local(version = version(LocalId, hlc = 10)), version(PeerId, hlc = 30), "test"),
        ).getOrThrow()

        assertTrue(file.exists())
    }

    private fun source(
        resolution: SyncMode.Mirror.ConflictResolution = SyncMode.Mirror.ConflictResolution.LastWriteWins,
    ): SourceEntry = sourceEntry(
        location = SourceLocation.Directory(root.absolutePath),
        syncMode = SyncMode.Mirror(resolution),
    )

    private fun local(
        state: FileRecord.State = FileRecord.State.Present(),
        hash: String = "mine",
        version: FileVersion = version(LocalId, hlc = 10),
    ) = FileRecord(
        id = FileId(FileIdValue),
        path = "Report.txt",
        locator = locator,
        state = state,
        content = ContentHash(hash, "SHA-256"),
        metadata = FileRecord.Metadata(size = 4, lastModified = TestEpoch, version = version),
    )

    private fun remote(version: FileVersion) = FileRecord(
        id = FileId(FileIdValue),
        path = "Report.txt",
        locator = null,
        state = FileRecord.State.Present(),
        content = ContentHash("theirs", "SHA-256"),
        metadata = FileRecord.Metadata(size = 6, lastModified = TestEpoch, version = version),
    )

    private suspend fun index(record: FileRecord) = storage.index.markProcessed(
        listOf(record.toIndexed(id = "row-1", sourceId = SourceId, locator = locator, currentTime = TestEpoch))
    )

    private suspend fun confirmCopy(hash: String) = storage.remoteIndex.replace(
        sourceId = SourceId,
        deviceId = PeerId,
        files = listOf(
            RemoteIndexedFile(
                sourceId = SourceId,
                fileId = FileIdValue,
                path = "Report.txt",
                state = LocalIndexedFile.State.Present(),
                size = FileSize(4),
                modifiedAt = TestEpoch,
                hash = ContentHash(hash, "SHA-256"),
                version = local().metadata.version?.toIndexed(),
                seenAt = TestEpoch,
            )
        ),
    )

    private fun version(device: String, hlc: Long, count: Long = 1) =
        FileVersion(vector = VersionVector(mapOf(device to count)), hlc = hlc, originDevice = device)

    private companion object {
        const val LocalId = "device-local"
        const val PeerId = "device-peer"
        const val SourceId = "source-1"
        const val FileIdValue = "file-1"
        val Evicted = FileRecord.State.Evicted(TestEpoch)
        val Key = IndexedFileKey(fileId = FileIdValue, sourceId = SourceId)
    }
}
