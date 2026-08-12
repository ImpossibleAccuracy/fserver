package com.fserver.core.domain.repository

import com.fserver.core.domain.model.DetectionMethod
import com.fserver.core.domain.model.ForeignDevice
import com.fserver.core.domain.model.exception.DetectionFailedException
import com.fserver.core.domain.model.exception.MalformedQrException
import com.fserver.net.connection.ConnectionManager.IncomingRequest
import kotlinx.coroutines.flow.Flow

interface DevicesRepository {
    /**
     * Currently available devices, updated as they are found or lost.
     */
    val onlineDevices: Flow<List<ForeignDevice>>

    /**
     * Methods scanning right now. Per-method rather than a single flag: several run at
     * once, they finish at wildly different times, and the UI has to say which one the
     * user is still waiting on.
     */
    val runningScanningMethods: Flow<Set<DetectionMethod>>

    val incoming: Flow<IncomingRequest>

    /**
     * The device with [id], or null once it is no longer among [onlineDevices].
     */
    fun device(id: String): Flow<ForeignDevice?>

    /** Start advertising this device to others. May be long-running. */
    // TODO: migrate to owner-locked advertising
    suspend fun startAdvertising()

    /**
     * Runs [request] until it completes, publishing anything it finds through [onlineDevices].
     * May be long-running and/or never complete, depending on the request.
     */
    @Throws(DetectionFailedException::class)
    suspend fun startDetection(request: DetectionMethod): Result<Unit>

    suspend fun connect(deviceId: String): Result<Unit>

    /**
     * Attempts to connect to device by [deviceId].
     */
    suspend fun handshakeByDeviceId(deviceId: String): Result<ForeignDevice>

    /**
     * Attempts to connect to device with given arguments.
     */
    suspend fun handshake(host: String, port: Int?): Result<ForeignDevice>

    /**
     * Decodes a QR payload and attempts to connect to device.
     *
     * @throws MalformedQrException if the payload is not a valid QR code for a device.
     */
    suspend fun handshake(payload: String): Result<ForeignDevice>
}
