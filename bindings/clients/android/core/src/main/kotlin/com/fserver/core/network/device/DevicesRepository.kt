package com.fserver.core.network.device

import com.fserver.common.exception.DetectionFailedException
import com.fserver.common.exception.MalformedQrException
import com.fserver.core.network.auth.AuthCredentials
import com.fserver.core.network.auth.Greeting
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.device.model.PendingConfirmation
import com.fserver.core.network.TransportKind
import com.fserver.core.network.info.model.PeerLocator
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
    val runningScanningMethods: Flow<Set<TransportKind>>

    /** Methods this device is currently announcing itself over. */
    val advertisingMethods: Flow<Set<TransportKind.Automatic>>

    /**
     * Incoming connection requests, updated as they arrive and are accepted or rejected.
     */
    val incoming: Flow<IncomingConnection>

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
     * Closes the session with [deviceId]. Trust survives it — the device stays known and can be
     * reconnected without another code comparison. Succeeds when there was no session to close.
     */
    suspend fun disconnect(deviceId: String): Result<Unit>

    /**
     * Runs [request] until it completes, publishing anything it finds through [onlineDevices].
     * May be long-running and/or never complete, depending on the request.
     */
    @Throws(DetectionFailedException::class)
    suspend fun startDetection(request: TransportKind): Result<Unit>

    /**
     * The public greeting for the device behind [arguments] - versions and offered methods,
     * nothing trusted yet. Costs no user interaction; safe to call to fill in a UI before the
     * user commits to anything.
     *
     * @throws MalformedQrException if [arguments] is a [PeerLocator.QrPayload] that is
     * not a valid QR code for a device.
     */
    suspend fun probe(arguments: PeerLocator): Result<Greeting>

    /**
     * Runs the full handshake for the device behind [arguments], ending in a session.
     *
     * @param credentials what to present to the peer. Null takes whatever the two sides have in
     * common; set it when the user picked a method off a [Greeting] shown earlier.
     * @throws MalformedQrException if [arguments] is a [PeerLocator.QrPayload] that is
     * not a valid QR code for a device.
     */
    suspend fun connect(arguments: PeerLocator, credentials: AuthCredentials?): Result<Unit>

    /**
     * Announces this device over [method], and keeps it announced until it is stopped. Starting a
     * method already on the air does nothing.
     *
     * Per method rather than "advertise everywhere", for the same reason [startDetection] is:
     * Android gates several of the radios behind runtime permissions, so announcing over
     * everything installed would fail on whatever the user was never asked about.
     */
    @Throws(DetectionFailedException::class)
    suspend fun startAdvertising(method: TransportKind.Automatic): Result<Unit>

    /** Takes [method] off the air. Returns once it is actually stopped. */
    suspend fun stopAdvertising(method: TransportKind.Automatic)

    /** Takes every method off the air. */
    suspend fun stopAdvertising()
}
