package com.fserver.core.network.device.model

import com.fserver.core.network.TransportKind
import kotlin.time.Instant

/**
 * A [FailedContact] plus the one judgement a UI should not be making on its own: whether this is
 * worth showing as a fault.
 */
data class ReachabilityFailure(
    val contact: FailedContact,
    /** Whether to put this in front of the user as a fault rather than reporting the device as simply offline. */
    val isWarning: Boolean,
) {
    val deviceId: String get() = contact.deviceId
    val reason: FailedContact.Reason get() = contact.reason
    val failedAt: Instant get() = contact.failedAt
    val since: Instant get() = contact.since
    val attempts: Int get() = contact.attempts
    val transport: TransportKind? get() = contact.transport
}
