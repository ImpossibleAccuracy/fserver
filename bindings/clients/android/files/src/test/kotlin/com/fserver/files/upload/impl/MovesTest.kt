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

/** A rename is a deletion plus a new file; with the same bytes on both, no bytes need to move. */
class MovesTest {

    private val strategy = MirrorUploadStrategy()

    @Test
    fun `a local rename the remote still holds under the old path is a remote move`() = runTest {
        val actions = plan(
            local = listOf(
                file("old", "old.jpg", content = "x", version = mapOf(A to 2L), state = Deleted),
                file("new", "new.jpg", content = "x", version = mapOf(A to 1L)),
            ),
            remote = listOf(file("old", "old.jpg", content = "x", version = mapOf(A to 1L))),
        )

        val move = actions.single() as FileAction.MoveRemote
        assertEquals(FileId("old"), move.from.id)
        assertEquals(FileId("new"), move.to.id)
        assertEquals(mapOf(A to 1L), move.version?.vector?.counters)
        assertEquals(mapOf(A to 2L), move.deletedVersion?.vector?.counters)
    }

    @Test
    fun `a remote rename this device still holds under the old path is a local move`() = runTest {
        val actions = plan(
            local = listOf(file("old", "old.jpg", content = "x", version = mapOf(B to 1L))),
            remote = listOf(
                file("old", "old.jpg", content = "x", version = mapOf(B to 2L), state = Deleted),
                file("new", "new.jpg", content = "x", version = mapOf(B to 1L)),
            ),
        )

        val move = actions.single() as FileAction.MoveLocal
        assertEquals(FileId("old"), move.from.id)
        assertEquals(FileId("new"), move.to.id)
    }

    @Test
    fun `different bytes stay a send and a delete`() = runTest {
        val actions = plan(
            local = listOf(
                file("old", "old.jpg", content = "x", version = mapOf(A to 2L), state = Deleted),
                file("new", "new.jpg", content = "y", version = mapOf(A to 1L)),
            ),
            remote = listOf(file("old", "old.jpg", content = "x", version = mapOf(A to 1L))),
        )

        assertTrue(actions.any { it is FileAction.Upload })
        assertTrue(actions.any { it is FileAction.DeleteRemote })
    }

    @Test
    fun `an unhashed new file is sent, not guessed into a move`() = runTest {
        val actions = plan(
            local = listOf(
                file("old", "old.jpg", content = "x", version = mapOf(A to 2L), state = Deleted),
                file("new", "new.jpg", content = null, version = mapOf(A to 1L)),
            ),
            remote = listOf(file("old", "old.jpg", content = "x", version = mapOf(A to 1L))),
        )

        assertTrue(actions.none { it is FileAction.MoveRemote })
    }

    @Test
    fun `each old file is moved once, however many copies were made of it`() = runTest {
        val actions = plan(
            local = listOf(
                file("old", "old.jpg", content = "x", version = mapOf(A to 2L), state = Deleted),
                file("new-1", "new-1.jpg", content = "x", version = mapOf(A to 1L)),
                file("new-2", "new-2.jpg", content = "x", version = mapOf(A to 1L)),
            ),
            remote = listOf(file("old", "old.jpg", content = "x", version = mapOf(A to 1L))),
        )

        assertEquals(1, actions.count { it is FileAction.MoveRemote })
        assertEquals(1, actions.count { it is FileAction.Upload })
        assertTrue(actions.none { it is FileAction.DeleteRemote })
    }

    private suspend fun plan(local: List<FileRecord>, remote: List<FileRecord>): List<FileAction> =
        strategy.plan(MirrorUploadStrategy.Params, FilesSnapshot(local, remote)).actions

    private fun file(
        id: String,
        path: String,
        content: String?,
        version: Map<String, Long>,
        state: FileRecord.State = FileRecord.State.Present(),
    ) = FileRecord(
        id = FileId(id),
        path = path,
        locator = path,
        state = state,
        content = content?.let { ContentHash(value = it, algorithm = "SHA-256") },
        metadata = FileRecord.Metadata(
            size = 64,
            lastModified = Early,
            version = FileVersion(VersionVector(version), hlc = 0, originDevice = version.keys.first()),
        ),
    )
}
