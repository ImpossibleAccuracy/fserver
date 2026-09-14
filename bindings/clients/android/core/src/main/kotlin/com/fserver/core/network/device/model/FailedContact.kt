package com.fserver.core.network.device.model

import com.fserver.core.network.TransportKind
import kotlin.time.Instant

/** A run of failed attempts to reach one device, as it is written down. */
data class FailedContact(
    val deviceId: String,
    val reason: Reason,
    /** The latest attempt in this run. */
    val failedAt: Instant,
    /** The first attempt in it - the moment the device stopped answering. */
    val since: Instant,
    /** How many attempts in this run have failed. Never below 1. */
    val attempts: Int,
    /** The transport the latest attempt went out over, when there was one to name. */
    val transport: TransportKind?,
    /** What the failure said, for a diagnostics line. Never the thing a user is shown. */
    val detail: String?,
) {
    /**
     * What the user can do about it differs per case, which is the whole reason these are apart:
     * [NoRoute] wants an address, [NotAllowed] wants a permission, [Refused] wants the peer's user.
     */
    enum class Reason {
        /** Nothing to dial: no route on record, and discovery is not reporting the device now. */
        NoRoute,

        /** There was something to dial and it did not answer. */
        Unreachable,

        /** It answered and turned the request down. */
        Refused,

        /** The OS is still withholding something the attempt needs - a permission, a radio. */
        NotAllowed,

        /** Reached, but the exchange broke before a session was up. */
        Failed,
    }
}
