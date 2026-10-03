package com.fserver.files.upload.impl

import com.fserver.files.upload.Causality
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.VersionVector
import com.fserver.files.upload.impl.OneWayUploadStrategy.EvictCriterion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local always wins, and nothing the remote does ever reaches local data. */
class OneWayUploadStrategyTest {

    private val strategy = OneWayUploadStrategy()

    @Test
    fun `a remote-only file is left alone`() = runTest {
        assertNull(plan(local = null, remote = record(vector = mapOf(B to 1L), content = "theirs")))
    }

    @Test
    fun `a remote edit is overwritten under a version covering both`() = runTest {
        val local = record(vector = mapOf(A to 1L), content = "mine")
        val remote = record(vector = mapOf(A to 1L, B to 1L), content = "theirs", origin = B)

        val upload = plan(local, remote) as FileAction.Upload

        val vector = checkNotNull(upload.version).vector
        assertTrue(vector.compare(local.metadata.version!!.vector) == Causality.Newer)
        assertTrue(vector.compare(remote.metadata.version!!.vector) != Causality.Older)
    }

    @Test
    fun `concurrent edits never conflict and never download`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 2L), content = "mine"),
            remote = record(vector = mapOf(A to 1L, B to 1L), content = "theirs"),
        )

        assertEquals(VersionVector(mapOf(A to 2L, B to 1L)), (action as FileAction.Upload).version?.vector)
    }

    @Test
    fun `a remote deletion of a file kept here is undone`() = runTest {
        val action = plan(
            local = record(vector = mapOf(A to 1L), content = "mine"),
            remote = record(vector = mapOf(A to 1L, B to 1L), state = Deleted),
        )

        assertTrue(action is FileAction.Upload)
    }

    @Test
    fun `a local deletion propagates only when asked to`() = runTest {
        val local = record(vector = mapOf(A to 2L), state = Deleted)
        val remote = record(vector = mapOf(A to 1L), content = "x")

        assertTrue(plan(local, remote, Params(propagateDeletions = true)) is FileAction.DeleteRemote)
        assertNull(plan(local, remote, Params(propagateDeletions = false)))
    }

    @Test
    fun `an evicted file plans nothing whatever the remote did`() = runTest {
        val local = record(vector = mapOf(A to 1L), content = "x", state = Evicted)

        assertNull(plan(local, record(vector = mapOf(A to 1L, B to 1L), state = Deleted)))
        assertNull(plan(local, record(vector = mapOf(A to 1L, B to 1L), content = "y")))
    }

    @Test
    fun `an old file the remote never had is skipped, one it has is still kept in step`() = runTest {
        val params = Params(skipModifiedBefore = Late)

        assertNull(plan(record(vector = mapOf(A to 1L), content = "x"), null, params))
        assertTrue(
            plan(
                local = record(vector = mapOf(A to 2L), content = "new"),
                remote = record(vector = mapOf(A to 1L), content = "old"),
                params = params,
            ) is FileAction.Upload
        )
    }

    @Test
    fun `a file the remote confirmably holds is evicted once due, unless pinned or fetched`() = runTest {
        val params = Params(evictWhen = EvictCriterion.NotModifiedFor(Now - Late))
        val remote = record(vector = mapOf(A to 1L), content = "x")

        assertTrue(plan(record(vector = mapOf(A to 1L), content = "x", modifiedAt = Early), remote, params) is FileAction.EvictLocal)
        assertNull(plan(record(vector = mapOf(A to 1L), content = "x", modifiedAt = Late), remote, params))
        assertNull(plan(record(vector = mapOf(A to 1L), content = "x", state = Pinned), remote, params))
        assertNull(plan(record(vector = mapOf(A to 1L), content = "x", state = Fetched), remote, params))
    }

    @Test
    fun `least recently used file is evicted, one with unknown access time never`() = runTest {
        val params = Params(evictWhen = EvictCriterion.NotAccessedFor(Now - Late))
        val remote = record(vector = mapOf(A to 1L), content = "x")

        assertTrue(plan(record(vector = mapOf(A to 1L), content = "x", accessedAt = Early), remote, params) is FileAction.EvictLocal)
        assertNull(plan(record(vector = mapOf(A to 1L), content = "x", accessedAt = Late), remote, params))
        assertNull(plan(record(vector = mapOf(A to 1L), content = "x", accessedAt = null), remote, params))
    }

    @Test
    fun `eviction waits for the hash and for histories to agree`() = runTest {
        val params = Params(evictWhen = EvictCriterion.LargerThan(0))

        assertTrue(
            plan(record(vector = mapOf(A to 1L)), record(vector = mapOf(A to 1L), content = "x"), params)
                    is FileAction.ComputeHash
        )
        assertTrue(
            plan(
                local = record(vector = mapOf(A to 1L), content = "x"),
                remote = record(vector = mapOf(A to 1L, B to 1L), content = "x"),
                params = params,
            ) is FileAction.MergeVersion
        )
    }

    private suspend fun plan(
        local: FileRecord?,
        remote: FileRecord?,
        params: OneWayUploadStrategy.Params = Params(),
    ): FileAction? = strategy.planOne(params, local, remote)

    @Suppress("TestFunctionName")
    private fun Params(
        propagateDeletions: Boolean = true,
        skipModifiedBefore: kotlin.time.Instant? = null,
        evictWhen: EvictCriterion? = null,
    ) = OneWayUploadStrategy.Params(propagateDeletions, skipModifiedBefore, evictWhen)
}
