package com.fserver.core.domain.repository

import com.fserver.core.domain.model.AuthMethod
import com.fserver.core.domain.model.ConnectionArguments
import com.fserver.core.domain.model.DetectionMethod
import com.fserver.core.domain.model.ForeignDevice
import com.fserver.core.domain.model.Greeting
import com.fserver.core.domain.model.PendingConfirmation
import com.fserver.core.domain.model.exception.DetectionFailedException
import com.fserver.core.domain.model.exception.MalformedQrException
import com.fserver.net.connection.IncomingConnectionsManager
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

    /**
     * Incoming connection requests, updated as they arrive and are accepted or rejected.
     */
    val incoming: Flow<IncomingConnectionsManager.IncomingRequest>

    /**
     * The peer currently waiting on this device to compare its code, or null. Both [probe]/[connect]
     * and an accepted [incoming] request suspend on this while it is set - Э9 scaffold, see
     * `docs/Handshake Migration.md`.
     */
    val pendingConfirmation: Flow<PendingConfirmation?>

    /** Answers whoever is in [pendingConfirmation] */
    fun resolvePendingConfirmation(accept: Boolean)

    /** The device with [id], or null once it is no longer among [onlineDevices]. */
    fun device(id: String): Flow<ForeignDevice?>

    /**
     * Runs [request] until it completes, publishing anything it finds through [onlineDevices].
     * May be long-running and/or never complete, depending on the request.
     */
    @Throws(DetectionFailedException::class)
    suspend fun startDetection(request: DetectionMethod): Result<Unit>

    /**
     * The public greeting for the device behind [arguments] — versions and offered methods,
     * nothing trusted yet. Costs no user interaction; safe to call to fill in a UI before the
     * user commits to anything.
     *
     * @throws MalformedQrException if [arguments] is a [ConnectionArguments.QrPayload] that is
     * not a valid QR code for a device.
     */
    suspend fun probe(arguments: ConnectionArguments): Result<Greeting>

    /**
     * Runs the full handshake for the device behind [arguments], ending in a session.
     *
     * @param method which offered method to authenticate with. Null takes whatever the two sides
     * have in common; set it when the user picked one off a [Greeting] shown earlier.
     * @throws MalformedQrException if [arguments] is a [ConnectionArguments.QrPayload] that is
     * not a valid QR code for a device.
     */
    suspend fun connect(arguments: ConnectionArguments, method: AuthMethod? = null): Result<Unit>

    /** Create lease for advertising service */
    fun advertisingServiceLease(): ServiceLease
}
