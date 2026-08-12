package com.fserver.core.domain.repository

import com.fserver.core.domain.model.DetectionMethod
import com.fserver.core.domain.model.exception.DetectionFailedException
import com.fserver.core.domain.model.exception.MalformedQrException
import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.spi.TransportEndpoint
import kotlinx.coroutines.flow.Flow

interface DeviceDetectionRepository {
    /**
     * Currently available devices, updated as they are found or lost.
     */
    val onlineDevices: Flow<List<DiscoveredPeer>>

    /**
     * Methods scanning right now. Per-method rather than a single flag: several run at
     * once, they finish at wildly different times, and the UI has to say which one the
     * user is still waiting on.
     */
    val runningScanningMethods: Flow<Set<DetectionMethod>>

    /**
     * The device with [id], or null once it is no longer among [onlineDevices].
     */
    fun device(id: String): Flow<DiscoveredPeer?>

    /** True if the device is currently among [onlineDevices]. */
    fun isDeviceOnline(id: String): Boolean

    suspend fun startAdvertising()

    /**
     * Runs [request] until it completes, publishing anything it finds through [onlineDevices].
     * May be long-running and/or never complete, depending on the request.
     */
    @Throws(DetectionFailedException::class)
    suspend fun startDetection(request: DetectionMethod): Result<Unit>

    /**
     * Decodes a QR payload into a [TransportEndpoint] that can be used to connect to the device.
     * @throws MalformedQrException if the payload is not a valid QR code for a device.
     */
    @Throws(MalformedQrException::class)
    suspend fun decodeQrPayload(payload: String): TransportEndpoint
}
