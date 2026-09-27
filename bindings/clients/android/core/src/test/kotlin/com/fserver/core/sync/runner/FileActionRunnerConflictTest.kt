package com.fserver.core.sync.runner

import android.content.ContextWrapper
import com.fserver.common.model.ContentHash
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.scan.toFiles
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.conflict.ConflictDecision
import com.fserver.core.sync.conflict.seenVersion
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.version.HlcTimestamp
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** An asking source holds a conflict until the user decides, and never acts on a stale decision. */
class FileActionRunnerConflictTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(localDeviceId = LocalId, clock = clock)
    private val node = FilesNode.create(ContextWrapper(null))
    private val downloader = mockk<FileDownloader>()

    private val runner = FileActionRunner(
        storage = storage,
        localIndexer = mockk(relaxed = true),
        remoteFetcher = mockk(relaxed = true),
        fileUploader = mockk(relaxed = true),
        fileDownloader = downloader,
        timeProvider = clock,
        node = node,
    )

    private lateinit var root: File
    private lateinit var source: SourceEntry
    private lateinit var conflict: FileAction.Conflict

    @Before
    fun setUp() = runTest {
        root = temp.newFolder("source-root")
        File(root, "docs").mkdirs()
        File(root, "docs/Report.txt").writeText("mine")

        source = sourceEntry(
            location = SourceLocation.Directory(root.absolutePath),
            syncMode = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.Ask),
        )

        val locator = node.openSource(source.location.toFiles()).scan().result().getOrThrow().single().locator

        conflict = FileAction.Conflict(
            local = record(locator, hash = "mine", version = version(LocalId, hlc = 10)),
            remote = record(null, hash = "theirs", version = version(PeerId, hlc = 20)),
            reason = "edited on both sides",
        )

        coEvery { downloader.download(any(), any(), any(), any()) } throws IllegalStateException("peer gone")
    }

    @Test
    fun `without a decision nothing moves`() = runTest {
        runner.execute(source, conflict).getOrThrow()

        coVerify(exactly = 0) { downloader.download(any(), any(), any(), any()) }
        assertEquals("mine", File(root, "docs/Report.txt").readText())
    }

    @Test
    fun `a decision over a version the user never saw is dropped`() = runTest {
        decide(ConflictDecision.Choice.KeepRemote, local = ConflictDecision.SeenVersion(
            hlc = HlcTimestamp(5),
            originDevice = LocalId,
        ))

        runner.execute(source, conflict).getOrThrow()

        coVerify(exactly = 0) { downloader.download(any(), any(), any(), any()) }
        assertNull(storage.conflictDecisions.find(Key))
    }

    @Test
    fun `keep both copies local bytes aside once, even when the transfer fails`() = runTest {
        decide(ConflictDecision.Choice.KeepBoth)

        assertTrue(runner.execute(source, conflict).isFailure)

        assertEquals("mine", File(root, "docs/Report (Local device).txt").readText())
        // The copy is done: the retry only has the transfer left to do.
        assertEquals(ConflictDecision.Choice.KeepRemote, storage.conflictDecisions.find(Key)?.choice)
        assertFalse(File(root, "docs/Report (Local device 2).txt").exists())
    }

    private suspend fun decide(
        choice: ConflictDecision.Choice,
        local: ConflictDecision.SeenVersion? = conflict.local.seenVersion(),
    ) = storage.conflictDecisions.put(
        ConflictDecision(
            sourceId = source.id,
            fileId = FileIdValue,
            choice = choice,
            local = local,
            remote = conflict.remote.seenVersion(),
            decidedAt = TestEpoch,
        )
    )

    private fun record(locator: String?, hash: String, version: FileVersion) = FileRecord(
        id = FileId(FileIdValue),
        path = "docs/Report.txt",
        locator = locator,
        state = FileRecord.State.Present(),
        content = ContentHash(hash, "SHA-256"),
        metadata = FileRecord.Metadata(size = 4, lastModified = TestEpoch, version = version),
    )

    private fun version(device: String, hlc: Long) =
        FileVersion(vector = VersionVector(mapOf(device to 1L)), hlc = hlc, originDevice = device)

    private companion object {
        const val LocalId = "device-local"
        const val PeerId = "device-peer"
        const val FileIdValue = "file-1"
        val Key = IndexedFileKey(fileId = FileIdValue, sourceId = "source-1")
    }
}
