package com.fserver.core.network.device.impl

import com.fserver.common.exception.NetworkException
import com.fserver.common.exception.SyncException
import com.fserver.core.network.DeviceUnreachableException
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.network.device.DeviceReachability
import com.fserver.core.network.device.model.FailedContact
import com.fserver.core.network.device.model.ReachabilityFailure
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.store.FServerStorage
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal class ReachabilityTracker(
    private val storage: FServerStorage,
    private val timeProvider: TimeProvider,
) : DeviceReachability {

    override val failures: Flow<List<ReachabilityFailure>> = combine(
        storage.trust.failedContacts,
        storage.trust.devices,
    ) { contacts, trusted ->
        val lastSeen = trusted.lastSeenByDevice()

        contacts
            .sortedByDescending { it.failedAt }
            .map { contact -> contact.judge(lastSeen[contact.deviceId]) }
    }.distinctUntilChanged()

    override fun device(deviceId: String): Flow<ReachabilityFailure?> = failures
        .map { failures -> failures.find { it.deviceId == deviceId } }
        .distinctUntilChanged()

    /** Ends the run recorded for [deviceId]: it has just been reached. */
    suspend fun recordSuccess(deviceId: String) {
        storage.trust.clearFailedContact(deviceId)
    }

    /**
     * Extends the run [deviceId] is in, or starts one.
     *
     * Only the latest attempt is described, but the run it belongs to is carried forward: one
     * failed attempt and a fortnight of them are not the same news.
     */
    suspend fun recordFailure(deviceId: String, cause: Throwable) {
        val now = timeProvider.now()
        val previous = storage.trust.findFailedContact(deviceId)

        storage.trust.recordFailedContact(
            FailedContact(
                deviceId = deviceId,
                reason = cause.asReason(),
                failedAt = now,
                since = previous?.since ?: now,
                attempts = (previous?.attempts ?: 0) + 1,
                transport = (cause as? DeviceUnreachableException)?.transport,
                detail = cause.message,
            )
        )
    }

    /**
     * [lastSeen] is when a handshake with the device last completed, which is the only measure of
     * "gone" that outlives a process. Null - nothing ever recorded a contact - is not treated as
     * long gone: unknown is not the same as missing.
     */
    private fun FailedContact.judge(lastSeen: Instant?) = ReachabilityFailure(
        contact = this,
        isWarning = when (reason) {
            FailedContact.Reason.Refused,
            FailedContact.Reason.NotAllowed,
            FailedContact.Reason.Failed -> true

            FailedContact.Reason.NoRoute,
            FailedContact.Reason.Unreachable ->
                attempts >= MinAttempts &&
                        lastSeen != null &&
                        timeProvider.now() - lastSeen >= GoneAfter
        },
    )

    private fun List<TrustedDevice>.lastSeenByDevice(): Map<String, Instant> = this
        .mapNotNull { device -> device.metadata?.lastSeen?.let { device.deviceId to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, seen) -> seen.max() }

    private companion object {
        /** One failure is a device that happens to be off; the second is a pattern. */
        const val MinAttempts = 2

        /** How long out of contact stops reading as "off right now" and starts reading as a fault. */
        val GoneAfter = 1.days
    }
}

/**
 * A cause is classified by what the user could do about it, so transport that names its own
 * failure differently still lands in the case that fits.
 */
private fun Throwable.asReason(): FailedContact.Reason = when (this) {
    is RequirementsNotMetException -> FailedContact.Reason.NotAllowed

    is DeviceUnreachableException ->
        if (transport == null) FailedContact.Reason.NoRoute
        else FailedContact.Reason.Unreachable

    is NetworkException.NoRoute -> FailedContact.Reason.NoRoute

    is NetworkException.Transport,
    is NetworkException.RequestTimeout,
    is NetworkException.SessionLinkLost,
    is NetworkException.SessionClosed -> FailedContact.Reason.Unreachable

    is NetworkException.AuthenticationRejected,
    is SyncException.RemoteRejectedException -> FailedContact.Reason.Refused

    else -> cause?.asReason() ?: FailedContact.Reason.Failed
}
