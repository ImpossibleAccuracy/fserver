package com.fserver.core.sync.limits

import com.fserver.common.model.FileSize
import com.fserver.core.support.TestEpoch
import com.fserver.core.sync.model.SourceEntry.Preferences.FileLimits
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FilesSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PlannedLimitsTest {

    @Test
    fun `no limits keeps every action`() {
        val actions = listOf(upload(file("a")), download(file("b")))

        val plan = FileLimits.None.limit(snapshot(), actions)

        assertEquals(actions, plan.runnable)
        assertEquals(emptyList<FileAction>(), plan.overLimit)
    }

    @Test
    fun `uploads are never held back by our own limits`() {
        val uploads = listOf(upload(file("a", size = 50)), upload(file("b", size = 50)))

        val plan = FileLimits(maxFiles = 1, maxTotalSize = FileSize(10))
            .limit(snapshot(local = listOf(file("held"))), uploads)

        assertEquals(uploads, plan.runnable)
    }

    @Test
    fun `newest new files are taken first up to the count`() {
        val old = download(file("old", modified = TestEpoch))
        val new = download(file("new", modified = TestEpoch + 5.minutes))

        val plan = FileLimits(maxFiles = 1, maxTotalSize = null).limit(snapshot(), listOf(old, new))

        assertEquals(listOf(new), plan.runnable)
        assertEquals(listOf(old), plan.overLimit)
    }

    @Test
    fun `files we already hold use up the budget`() {
        val plan = FileLimits(maxFiles = 1, maxTotalSize = null)
            .limit(snapshot(local = listOf(file("held"))), listOf(download(file("fresh"))))

        assertEquals(emptyList<FileAction>(), plan.runnable)
    }

    @Test
    fun `an update growing past the total size is left out`() {
        val update = download(file("held", size = 11))

        val plan = FileLimits(maxFiles = 1, maxTotalSize = FileSize(10))
            .limit(snapshot(local = listOf(file("held", size = 1))), listOf(update))

        assertEquals(listOf(update), plan.overLimit)
    }

    @Test
    fun `a shrinking update passes even over the limit`() {
        val update = download(file("held", size = 5))

        val plan = FileLimits(maxFiles = 1, maxTotalSize = FileSize(1))
            .limit(snapshot(local = listOf(file("held", size = 8))), listOf(update))

        assertEquals(listOf(update), plan.runnable)
    }

    @Test
    fun `an update's growth is booked before new files`() {
        val update = download(file("held", size = 8))
        val fresh = download(file("fresh", size = 3, modified = TestEpoch + 5.minutes))

        val plan = FileLimits(maxFiles = null, maxTotalSize = FileSize(10))
            .limit(snapshot(local = listOf(file("held", size = 4))), listOf(fresh, update))

        assertEquals(listOf(update), plan.runnable)
        assertEquals(listOf(fresh), plan.overLimit)
    }

    @Test
    fun `a file too big to fit does not block smaller older ones`() {
        val big = download(file("big", size = 20, modified = TestEpoch + 5.minutes))
        val small = download(file("small", size = 5))

        val plan = FileLimits(maxFiles = null, maxTotalSize = FileSize(10))
            .limit(snapshot(), listOf(big, small))

        assertEquals(listOf(small), plan.runnable)
    }

    @Test
    fun `evicted and deleted files hold no room`() {
        val local = listOf(
            file("gone", state = FileRecord.State.Deleted(TestEpoch)),
            file("evicted", state = FileRecord.State.Evicted(TestEpoch)),
        )

        val plan = FileLimits(maxFiles = 1, maxTotalSize = null)
            .limit(snapshot(local = local), listOf(download(file("fresh"))))

        assertEquals(1, plan.runnable.size)
    }

    private fun snapshot(local: List<FileRecord> = emptyList()) = FilesSnapshot(local, emptyList())

    private fun file(
        id: String,
        size: Long = 1,
        modified: Instant = TestEpoch,
        state: FileRecord.State = FileRecord.State.Present(),
    ) = FileRecord(
        id = FileId(id),
        path = "$id.bin",
        locator = id,
        state = state,
        content = null,
        metadata = FileRecord.Metadata(size = size, lastModified = modified, version = null),
    )

    private fun upload(file: FileRecord) = FileAction.Upload(file, version = null, reason = "test")

    private fun download(file: FileRecord) = FileAction.Download(file, version = null, reason = "test")
}
