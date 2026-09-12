package com.fserver.core.network.device

import com.fserver.common.exception.DetectionFailedException
import com.fserver.core.network.TransportKind
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
