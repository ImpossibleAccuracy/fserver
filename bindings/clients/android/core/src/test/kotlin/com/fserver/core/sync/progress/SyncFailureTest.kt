package com.fserver.core.sync.progress

import com.fserver.common.exception.NetworkException
import com.fserver.common.exception.SyncException
import com.fserver.common.exception.TransferException
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.requirement.Requirement
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.support.MutableTimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * What a pass reports when it gave up.
 *
 * The point of typing it: a host holding only a log line reports every failure with the same
 * sentence, and the one failure the user could actually clear is the one that reads like all the
 * others.
 */
class SyncFailureTest {

    @Test
    fun `a pass stopped by the os carries the report the host needs to offer a fix`() {
        val report = RequirementReport(
            blockers = emptyList(),
            solvable = listOf(Requirement.SystemToggle(Requirement.SystemToggle.Kind.WIFI)),
        )

        val failure = RequirementsNotMetException(report).toSyncFailure()

        assertEquals(SyncFailure.Reason.NotAllowed, failure.reason)
        assertSame(report, failure.requirements)
    }

    @Test
    fun `a peer that could not be dialled is unreachable, not a broken pass`() {
        val failure = NetworkException.RequestTimeout(TIMEOUT).toSyncFailure()

        assertEquals(SyncFailure.Reason.Unreachable, failure.reason)
        assertNull(failure.requirements)
    }

    @Test
    fun `a refusal is the peer's answer, not a fault on this side`() {
        val failure = SyncException.RemoteRejectedException("source is gone").toSyncFailure()

        assertEquals(SyncFailure.Reason.Refused, failure.reason)
        assertEquals("source is gone", failure.detail)
    }

    @Test
    fun `failed actions are read through the wrapper that collects them`() {
        val wrapper = SyncException.ActionFailedException("2 errors").apply {
            addSuppressed(TransferException.FileNotFoundException("photo.jpg"))
            // One action stopped on a permission, which explains the pass rather than one file.
            addSuppressed(RequirementsNotMetException(RequirementReport.Satisfied))
        }

        val failure = wrapper.toSyncFailure()

        assertEquals(SyncFailure.Reason.NotAllowed, failure.reason)
        assertSame(RequirementReport.Satisfied, failure.requirements)
    }

    @Test
    fun `files that simply would not copy are reported as that and nothing more`() {
        val wrapper = SyncException.ActionFailedException("1 error").apply {
            addSuppressed(TransferException.FileNotFoundException("photo.jpg"))
        }

        assertEquals(SyncFailure.Reason.TransferFailed, wrapper.toSyncFailure().reason)
    }

    @Test
    fun `a pass that never got its lease is still recorded against the source`() = runTest {
        val reporter = SyncProgressReporter(MutableTimeProvider())

        reporter.localPassAborted(SourceId, NetworkException.NoRoute("nowhere to dial"))

        // Without this the source would simply not have moved, with nothing to say why.
        val pass = reporter.pass(SourceId).first() as SourcePass.Local
        assertEquals(SourcePass.Local.Stage.Failed, pass.stage)
        assertEquals(SyncFailure.Reason.Unreachable, pass.failure?.reason)
    }

    private companion object {
        const val SourceId = "source-1"

        val TIMEOUT = 30.seconds
    }
}
