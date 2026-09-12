package com.fserver.core.network.dictionary.dto

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.support.TestEpoch
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.Revision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours

/**
 * The wire form of a file record, and the one distinction in it that destroys user data if it is
 * lost: evicted means "bytes freed here, still ours"; deleted means "the user threw it away".
 *
 * Four mappers sit between the index and the wire, and a state that survives three of them and
 * collapses in the fourth is the whole failure.
 */
class FileRecordMappingTest {

    private val evictedAt = TestEpoch + 1.hours
    private val deletedAt = TestEpoch + 2.hours

    @Test
    fun `an evicted local file stays evicted on the wire`() {
        val dto = localFile(LocalIndexedFile.State.Evicted(evictedAt)).toDto()

        assertEquals(FileRecordDto.State.Evicted(evictedAt), dto.state)
    }

    @Test
    fun `an evicted record from the wire stays evicted as a file record`() {
        val record = fileDtoWith(FileRecordDto.State.Evicted(evictedAt)).toFileRecord()

        assertEquals(FileRecord.State.Evicted(evictedAt), record.state)
    }

    @Test
    fun `an evicted record from the wire stays evicted in the remote index`() {
        val remote = fileDtoWith(FileRecordDto.State.Evicted(evictedAt)).toRemoteIndexed(TestEpoch)

        assertEquals(LocalIndexedFile.State.Evicted(evictedAt), remote.state)
    }

    @Test
    fun `an evicted file record stays evicted on its way out`() {
        val dto = fileRecord(FileRecord.State.Evicted(evictedAt)).toDto(sourceId = "source-1")

        assertEquals(FileRecordDto.State.Evicted(evictedAt), dto.state)
    }

    @Test
    fun `a deletion keeps its own moment across the wire`() {
        val dto = localFile(LocalIndexedFile.State.Deleted(deletedAt)).toDto()

        assertEquals(FileRecordDto.State.Deleted(deletedAt), dto.state)
        assertEquals(FileRecord.State.Deleted(deletedAt), dto.toFileRecord().state)
    }

    @Test
    fun `a pinned file stays pinned, so eviction keeps skipping it`() {
        val dto = localFile(LocalIndexedFile.State.Present(pinned = true)).toDto()

        assertEquals(FileRecordDto.State.Present(pinned = true), dto.state)
        assertEquals(FileRecord.State.Present(pinned = true), dto.toFileRecord().state)
    }

    @Test
    fun `hash and revision survive the round trip, so nothing is re-uploaded for nothing`() {
        val dto = localFile(LocalIndexedFile.State.Present()).toDto()
        val record = dto.toFileRecord()

        assertEquals(ContentHash("abc123", "SHA-256"), record.content)
        assertEquals(Revision(originDevice = "device-peer", counter = 7), record.metadata.revision)
        assertEquals(TestEpoch, record.metadata.lastModified)
        assertEquals(64L, record.metadata.size)
    }

    @Test
    fun `the remote index records when we heard it, not when it happened`() {
        val heardAt = TestEpoch + 5.hours

        val remote = fileDtoWith(FileRecordDto.State.Present()).toRemoteIndexed(heardAt)

        assertEquals(heardAt, remote.seenAt)
        assertEquals(TestEpoch, remote.modifiedAt)
        assertTrue(remote.state is LocalIndexedFile.State.Present)
    }

    private fun localFile(state: LocalIndexedFile.State) = LocalIndexedFile(
        id = "row-1",
        sourceId = "source-1",
        fileId = "file-1",
        path = "photo.jpg",
        locator = "/tmp/photo.jpg",
        state = state,
        size = FileSize(64),
        modifiedAt = TestEpoch,
        hash = ContentHash("abc123", "SHA-256"),
        revision = LocalIndexedFile.Revision(originDevice = "device-peer", counter = 7),
        processedAt = TestEpoch,
    )

    private fun fileRecord(state: FileRecord.State) = FileRecord(
        id = FileId("file-1"),
        path = "photo.jpg",
        locator = null,
        state = state,
        content = null,
        metadata = FileRecord.Metadata(size = 64, lastModified = TestEpoch, revision = null),
    )

    private fun fileDtoWith(state: FileRecordDto.State) = FileRecordDto(
        id = "file-1",
        sourceId = "source-1",
        path = "photo.jpg",
        state = state,
        content = null,
        metadata = FileRecordDto.Metadata(
            size = 64,
            lastModified = TestEpoch,
            revision = null,
        ),
    )
}
