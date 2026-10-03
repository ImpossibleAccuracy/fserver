package com.fserver.core.network.device

import com.fserver.common.exception.MalformedQrException
import com.fserver.core.network.auth.AuthCredentials
import com.fserver.core.network.auth.Greeting
import com.fserver.core.network.device.model.PendingConfirmation
import com.fserver.core.network.info.model.PeerLocator
import kotlinx.coroutines.flow.Flow

/**
 * Reaching other devices: who is visible ([devices]), how to look ([discovery]), and what it takes
 * to open a session with one.
 *
 * The halves are split off rather than inlined because they answer different questions and are used
 * apart: a settings screen reads [devices] without ever starting a scan, and the advertising
 * lifecycle drives [advertising] without caring who is out there.
 */
interface DevicesRepository {
    /** Looking for other devices, and what is scanning right now. */
    val discovery: DeviceDiscovery

    /** Being findable, and what is on the air right now. */
    val advertising: DeviceAdvertising

    /** Who is visible, split by how strong the claim is. */
    val devices: OnlineDevices

    /** Incoming connection requests, updated as they arrive and are accepted or rejected. */
    val incoming: Flow<IncomingConnection>

    /** The peer currently waiting on this device to compare its code, or null. */
    val pendingConfirmation: Flow<PendingConfirmation?>

    /** Answers whoever is in [pendingConfirmation] */
    fun resolvePendingConfirmation(accept: Boolean)

    /**
     * Closes the session with [deviceId]. Trust survives it — the device stays known and can be
     * reconnected without another code comparison. Succeeds when there was no session to close.
     */
    suspend fun disconnect(deviceId: String): Result<Unit>

    /**
     * Drops every key recorded for [deviceId] and closes its session. The next handshake starts
     * over from a code comparison, which is the only way to revoke trust.
     */
    suspend fun forget(deviceId: String): Result<Unit>

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
     *
     * @throws MalformedQrException if [arguments] is a [PeerLocator.QrPayload] that is
     * not a valid QR code for a device.
     */
    suspend fun connect(arguments: PeerLocator, credentials: AuthCredentials?): Result<Unit>
}
