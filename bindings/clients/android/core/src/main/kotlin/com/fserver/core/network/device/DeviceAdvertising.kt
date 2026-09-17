package com.fserver.core.network.device

import com.fserver.common.exception.DetectionFailedException
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.model.DeviceInvitation
import kotlinx.coroutines.flow.Flow

/**
 * Being findable by other devices - the other half of [DeviceDiscovery].
 *
 * Only [TransportKind.Automatic] can announce: the rest have nothing to announce over. Per method
 * for the same reason a scan is, and gated on the same permissions, since an advertiser drives the
 * radios its scan listens on.
 */
interface DeviceAdvertising {
    /** Methods this device is currently announcing itself over. */
    val runningMethods: Flow<Set<TransportKind.Automatic>>

    /**
     * The connection code for this device, or null while nothing is listening - a code pointing at
     * an address nobody answers on is worse than no code at all. Independent of [runningMethods]:
     * a device that publishes nothing is still reachable by code, which is the whole point of the
     * QR access mode (`Terms of Reference.md` §3.2).
     */
    val invitation: Flow<DeviceInvitation?>

    /**
     * Announces this device over [method], and keeps it announced until it is stopped. Starting a
     * method already on the air does nothing.
     */
    @Throws(DetectionFailedException::class)
    suspend fun start(method: TransportKind.Automatic): Result<Unit>

    /** Takes [method] off the air. Returns once it is actually stopped. */
    suspend fun stop(method: TransportKind.Automatic)

    /** Takes every method off the air. */
    suspend fun stopAll()
}
