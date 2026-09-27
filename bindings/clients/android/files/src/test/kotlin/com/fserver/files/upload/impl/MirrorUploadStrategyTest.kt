package com.fserver.files.upload.impl

import com.fserver.common.model.ContentHash
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.VersionVector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/** Which side is newer comes from version vectors alone - wall clocks may be arbitrarily wrong. */
class MirrorUploadStrategyTest {

    private val strategy = MirrorUploadStrategy()

    @Test
    fun `same content under different histories merges them instead of conflicting`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), content = "x", hlc = 20),
            remote = record(vector = mapOf(A to 1L, B to 1L), content = "x", hlc = 10, origin = B),
        )

        assertEquals(
            FileVersion(VersionVector(mapOf(A to 2L, B to 1L)), hlc = 20, originDevice = A),
            (action as FileAction.MergeVersion).version,
        )
    }

    @Test
    fun `same content and history needs nothing`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L), content = "x"),
            remote = record(vector = mapOf(A to 1L), content = "x"),
        )

        assertEquals(null, action)
    }

    @Test
    fun `deleted on both sides under different histories merges them`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), state = Deleted),
            remote = record(vector = mapOf(A to 1L, B to 1L), state = Deleted),
        )

        assertTrue(action is FileAction.MergeVersion)
    }

    @Test
    fun `newer locally uploads even when the local clock is behind`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L, B to 1L), content = "new", modifiedAt = Early),
            remote = record(vector = mapOf(A to 1L), content = "old", modifiedAt = Late),
        )

        assertTrue(action is FileAction.Upload)
    }

    @Test
    fun `newer remotely downloads even when the remote clock is behind`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L), content = "old", modifiedAt = Late),
            remote = record(vector = mapOf(A to 2L), content = "new", modifiedAt = Early),
        )

        assertTrue(action is FileAction.Download)
    }

    @Test
    fun `editing a file the peer wrote is not a conflict`() = runTest {
        // The old rule - "different last author means conflict" - flagged exactly this.
        val action = plan(
            local = record(vector = mapOf(A to 1L, B to 1L), content = "edited", origin = B),
            remote = record(vector = mapOf(A to 1L), content = "written", origin = A),
        )

        assertTrue(action is FileAction.Upload)
    }

    @Test
    fun `edits made apart are a conflict`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L, B to 1L), content = "b"),
            remote = record(vector = mapOf(A to 2L), content = "a"),
        )

        assertTrue(action is FileAction.Conflict)
    }

    @Test
    fun `a deletion newer than the remote version propagates`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), state = Deleted),
            remote = record(vector = mapOf(A to 1L), content = "x"),
        )

        assertEquals(VersionVector(mapOf(A to 2L)), (action as FileAction.DeleteRemote).version?.vector)
    }

    @Test
    fun `a remote deletion newer than the local version propagates`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L), content = "x"),
            remote = record(vector = mapOf(A to 1L, B to 1L), state = Deleted),
        )

        // Recorded as the peer's deletion, not as a new one of ours.
        assertEquals(VersionVector(mapOf(A to 1L, B to 1L)), (action as FileAction.DeleteLocal).version?.vector)
    }

    @Test
    fun `an unhashed local file is hashed before a remote deletion removes it`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L), content = null),
            remote = record(vector = mapOf(A to 1L, B to 1L), state = Deleted),
        )

        assertTrue(action is FileAction.ComputeHash)
    }

    @Test
    fun `an unhashed remote file is hashed before a local deletion removes it`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), state = Deleted),
            remote = record(vector = mapOf(A to 1L), content = null),
        )

        assertTrue(action is FileAction.ComputeHash)
    }

    @Test
    fun `a deletion concurrent with an edit is a conflict, not a silent delete`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), state = Deleted),
            remote = record(vector = mapOf(A to 1L, B to 1L), content = "edited"),
        )

        assertTrue(action is FileAction.Conflict)
    }

    @Test
    fun `an edit made after seeing the deletion brings the file back`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), state = Deleted),
            remote = record(vector = mapOf(A to 2L, B to 1L), content = "recreated"),
        )

        assertTrue(action is FileAction.Download)
    }

    @Test
    fun `an evicted file is not a deletion`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L), content = "x", state = Evicted),
            remote = record(vector = mapOf(A to 1L), content = "x"),
        )

        assertEquals(null, action)
    }

    @Test
    fun `an evicted file follows a newer remote deletion`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L), state = Evicted),
            remote = record(vector = mapOf(A to 2L), state = Deleted),
        )

        assertTrue(action is FileAction.DeleteLocal)
    }

    @Test
    fun `an evicted file is not refilled by a newer remote version`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L), content = "old", state = Evicted),
            remote = record(vector = mapOf(A to 2L), content = "new"),
        )

        assertEquals(null, action)
    }

    @Test
    fun `an evicted file newer than the remote one is a conflict`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), content = "new", state = Evicted),
            remote = record(vector = mapOf(A to 1L), content = "old"),
        )

        assertTrue(action is FileAction.Conflict)
    }

    @Test
    fun `an unhashed evicted file is never sent to be hashed`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L), content = null, state = Evicted),
            remote = record(vector = mapOf(A to 1L), content = "x"),
        )

        assertEquals(null, action)
    }

    @Test
    fun `a local-only evicted file is not uploaded`() = runTest {
        val decisions = strategy.plan(
            MirrorUploadStrategy.Params,
            FilesSnapshot(listOf(record(vector = mapOf(A to 1L), state = Evicted)), emptyList()),
        )

        assertTrue(decisions.isEmpty)
    }

    @Test
    fun `a remote-only evicted file is not downloaded`() = runTest {
        val decisions = strategy.plan(
            MirrorUploadStrategy.Params,
            FilesSnapshot(emptyList(), listOf(record(vector = mapOf(A to 1L), state = Evicted))),
        )

        assertTrue(decisions.isEmpty)
    }

    @Test
    fun `a newer local version is not pushed into a remote eviction`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), content = "new"),
            remote = record(vector = mapOf(A to 1L), content = "old", state = Evicted),
        )

        assertEquals(null, action)
    }

    @Test
    fun `a deletion older than a remote eviction leaves it alone`() = runTest {
        // Nobody here has the bytes, but the peer's backup may: deleting would reach it.
        val action = plan(
            local = record(vector = mapOf(A to 1L), state = Deleted),
            remote = record(vector = mapOf(A to 1L, B to 1L), state = Evicted),
        )

        assertEquals(null, action)
    }

    @Test
    fun `a deletion newer than a remote eviction propagates`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), state = Deleted),
            remote = record(vector = mapOf(A to 1L), state = Evicted),
        )

        assertTrue(action is FileAction.DeleteRemote)
    }

    @Test
    fun `evicted on both sides needs nothing`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), content = "new", state = Evicted),
            remote = record(vector = mapOf(A to 1L), content = "old", state = Evicted),
        )

        assertEquals(null, action)
    }

    @Test
    fun `unknown content is hashed before deciding`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L), content = null),
            remote = record(vector = mapOf(A to 2L), content = "x"),
        )

        assertTrue(action is FileAction.ComputeHash)
    }

    @Test
    fun `a versioned record is newer than one with no version`() = runTest {
        val action = plan(
            local = record(vector = null, content = "old"),
            remote = record(vector = mapOf(A to 1L), content = "new"),
        )

        assertTrue(action is FileAction.Download)
    }

    private suspend fun plan(local: FileRecord, remote: FileRecord): FileAction? =
        strategy.plan(MirrorUploadStrategy.Params, FilesSnapshot(listOf(local), listOf(remote)))
            .actions
            .singleOrNull()

    private fun record(
        vector: Map<String, Long>?,
        content: String? = null,
        state: FileRecord.State = FileRecord.State.Present(),
        modifiedAt: Instant = Early,
        origin: String = A,
        hlc: Long = 0,
    ) = FileRecord(
        id = FileId("file-1"),
        path = "photo.jpg",
        locator = "photo.jpg",
        state = state,
        content = content?.let { ContentHash(value = it, algorithm = "SHA-256") },
        metadata = FileRecord.Metadata(
            size = 64,
            lastModified = modifiedAt,
            version = vector?.let { FileVersion(VersionVector(it), hlc = hlc, originDevice = origin) },
        ),
    )

    private companion object {
        const val A = "device-a"
        const val B = "device-b"
        val Early: Instant = Instant.fromEpochSeconds(1_000_000)
        val Late: Instant = Instant.fromEpochSeconds(2_000_000)
        val Deleted = FileRecord.State.Deleted(Early)
        val Evicted = FileRecord.State.Evicted(Early)
    }
}
