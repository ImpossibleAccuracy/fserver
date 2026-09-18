package com.fserver.core.sync.progress

import kotlinx.serialization.Serializable

/**
 * The part of a [SyncFailure] that crosses to the other device,
 * carried by `AcquireSyncLease.ReleaseLease`.
 */
@Serializable
enum class SyncFailureReason {
    /** The OS on the other device is withholding a permission, a radio, or the network. */
    NotAllowed,

    /** The other device could not read or write the source. */
    SourceUnavailable,

    /** Files were planned and did not move. */
    TransferFailed,

    /** The pass gave up for a reason that does not travel. */
    Failed,
}

/** What this failure is worth telling the peer, and no more. */
internal fun SyncFailure.toWire(): SyncFailureReason = when (reason) {
    SyncFailure.Reason.NotAllowed -> SyncFailureReason.NotAllowed
    SyncFailure.Reason.SourceUnavailable -> SyncFailureReason.SourceUnavailable
    SyncFailure.Reason.TransferFailed -> SyncFailureReason.TransferFailed

    SyncFailure.Reason.Unreachable,
    SyncFailure.Reason.Refused,
    SyncFailure.Reason.NotConverged,
    SyncFailure.Reason.Failed,
        -> SyncFailureReason.Failed
}
