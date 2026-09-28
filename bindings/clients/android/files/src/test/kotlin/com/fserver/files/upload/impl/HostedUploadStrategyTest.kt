package com.fserver.files.upload.impl

import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The remote holds the files; local edits go out. */
class HostedUploadStrategyTest {

    private val strategy = HostedUploadStrategy()

    @Test
    fun `a remote-only file waits to be fetched on demand`() = runTest {
        assertNull(plan(null, record(vector = mapOf(B to 1L), content = "x")))
    }

    @Test
    fun `a cached copy the remote holds is evicted unless pinned or fetched on demand`() = runTest {
        val remote = record(vector = mapOf(B to 1L), content = "x")

        assertTrue(plan(record(vector = mapOf(B to 1L), content = "x"), remote) is FileAction.EvictLocal)
        assertNull(plan(record(vector = mapOf(B to 1L), content = "x", state = Pinned), remote))
        assertNull(plan(record(vector = mapOf(B to 1L), content = "x", state = Fetched), remote))
    }

    @Test
    fun `a remote edit refreshes the cache`() = runTest {
        val action = plan(
            local = record(vector = mapOf(B to 1L), content = "old"),
            remote = record(vector = mapOf(B to 2L), content = "new"),
        )

        assertTrue(action is FileAction.Download)
    }

    @Test
    fun `a local edit goes out`() = runTest {
        val local = record(vector = mapOf(A to 1L, B to 1L), content = "mine")
        val remote = record(vector = mapOf(B to 1L), content = "theirs")

        assertTrue(plan(local, remote) is FileAction.Upload)
    }

    @Test
    fun `a new local file goes out`() = runTest {
        val local = record(vector = mapOf(A to 1L), content = "mine")

        assertTrue(plan(local, null) is FileAction.Upload)
    }

    @Test
    fun `concurrent edits conflict`() = runTest {
        val local = record(vector = mapOf(A to 1L), content = "mine")
        val remote = record(vector = mapOf(B to 1L), content = "theirs")

        assertTrue(plan(local, remote) is FileAction.Conflict)
    }

    @Test
    fun `a remote deletion drops the cached copy, hashing it first when unhashed`() = runTest {
        val remote = record(vector = mapOf(B to 2L), state = Deleted)

        assertTrue(plan(record(vector = mapOf(B to 1L), content = "x"), remote) is FileAction.DeleteLocal)
        assertTrue(plan(record(vector = mapOf(B to 1L)), remote) is FileAction.ComputeHash)
        assertTrue(plan(record(vector = mapOf(B to 1L), state = Evicted), remote) is FileAction.DeleteLocal)
    }

    @Test
    fun `a local deletion reaches the remote`() = runTest {
        val local = record(vector = mapOf(A to 1L, B to 1L), state = Deleted)
        val remote = record(vector = mapOf(B to 1L), content = "x")

        assertTrue(plan(local, remote) is FileAction.DeleteRemote)
    }

    @Test
    fun `a local deletion older than a remote edit is only a stale cache entry`() = runTest {
        val action = plan(
            local = record(vector = mapOf(B to 1L), state = Deleted),
            remote = record(vector = mapOf(B to 2L), content = "x"),
        )

        assertNull(action)
    }

    private suspend fun plan(local: FileRecord?, remote: FileRecord?): FileAction? =
        strategy.planOne(HostedUploadStrategy.Params, local, remote)
}
