package com.fserver.core.sync.progress

import com.fserver.common.exception.FileSystemException
import com.fserver.common.exception.NetworkException
import com.fserver.common.exception.SyncException
import com.fserver.common.exception.TransferException
import com.fserver.core.files.SourceRequirementsNotMetException
import com.fserver.core.network.DeviceUnreachableException
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.requirement.RequirementReport

/**
 * Why a pass gave up, typed the way a failed dial is typed - see
 * [com.fserver.core.network.device.model.FailedContact.Reason].
 *
 * A pass can break for reasons that have nothing to do with each other - a radio that is off, a
 * peer that said no, a file that would not copy - and a host holding only a log line has to report
 * all of them the same way. [reason] is what the sentence is chosen from; [detail] is the engine's
 * own wording, worth a second line and never the whole message.
 */
data class SyncFailure(
    val reason: Reason,
    val detail: String?,
    /** Set when [reason] is [Reason.NotAllowed]: what the OS is still withholding. */
    val requirements: RequirementReport? = null,
) {
    /** Grouped by what the user could do about it, not by which exception was thrown. */
    enum class Reason {
        /** The peer could not be reached at all: no route, no answer, link lost mid-pass. */
        Unreachable,

        /** The peer answered and turned the pass down. */
        Refused,

        /** The OS is in the way - a permission, a radio, the network this device is on. */
        NotAllowed,

        /** The source itself could not be read or written on this device. */
        SourceUnavailable,

        /** Files were planned and did not move. */
        TransferFailed,

        /** The pass kept re-planning without converging and was cut off. */
        NotConverged,

        /** Anything the engine could not place. */
        Failed,
    }
}

/**
 * Reads a thrown cause as a [SyncFailure].
 *
 * The report is carried through rather than flattened to a sentence: a pass that stopped on a
 * permission is the one case where the host has a button to offer, and it needs the report to
 * draw it.
 */
internal fun Throwable.toSyncFailure(): SyncFailure = SyncFailure(
    reason = syncFailureReason(),
    detail = message,
    requirements = findRequirementReport(),
)

private fun Throwable.syncFailureReason(): SyncFailure.Reason = when (this) {
    is RequirementsNotMetException,
    is SourceRequirementsNotMetException,
        -> SyncFailure.Reason.NotAllowed

    is DeviceUnreachableException -> SyncFailure.Reason.Unreachable

    is NetworkException.NoRoute,
    is NetworkException.Transport,
    is NetworkException.RequestTimeout,
    is NetworkException.SessionClosed,
    is NetworkException.SessionLinkLost,
        -> SyncFailure.Reason.Unreachable

    is NetworkException.AuthenticationRejected,
    is NetworkException.Handshake,
    is SyncException.RemoteRejectedException,
        -> SyncFailure.Reason.Refused

    is SyncException.MaxRetriesExceededException -> SyncFailure.Reason.NotConverged

    // Every individual cause hangs off it as a suppressed exception; the actions that failed are
    // what went wrong, so the pass is judged by them rather than by the wrapper.
    is SyncException.ActionFailedException ->
        suppressedExceptions.firstNotNullOfOrNull { it.actionFailureReason() }
            ?: SyncFailure.Reason.TransferFailed

    is TransferException -> SyncFailure.Reason.TransferFailed

    is FileSystemException -> SyncFailure.Reason.SourceUnavailable

    else -> cause?.syncFailureReason() ?: SyncFailure.Reason.Failed
}

/**
 * What one failed action says about the pass, or null when it says nothing the pass as a whole
 * should be reported as.
 *
 * A transfer that failed on its own is the ordinary case and carries no more than
 * [SyncFailure.Reason.TransferFailed], which is the fallback anyway. Anything else - a radio, a
 * refusal, a source gone - explains the whole pass and is worth promoting.
 */
private fun Throwable.actionFailureReason(): SyncFailure.Reason? =
    syncFailureReason().takeUnless {
        it == SyncFailure.Reason.TransferFailed || it == SyncFailure.Reason.Failed
    }

private fun Throwable.findRequirementReport(): RequirementReport? = when (this) {
    is RequirementsNotMetException -> report
    is SourceRequirementsNotMetException -> report
    is SyncException.ActionFailedException ->
        suppressedExceptions.firstNotNullOfOrNull { it.findRequirementReport() }

    else -> cause?.findRequirementReport()
}
