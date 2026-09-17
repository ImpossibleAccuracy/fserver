package com.fserver.net.connection

import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.TransportEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Component to probe and connect to other devices, and to manage the sessions that result.
 *
 * Reaching a device takes two connections, not one:
 * - [probe] asks a stranger what it is willing to do;
 * - [connect] then dials again and says hello afresh on that link, because the greeting was
 * never trusted and the authentication has to be bound to the hello it actually ran under.
 *
 * @see [IncomingConnectionsManager] for the other side of the story.
 */
interface RequestManager<M : Any> {
    /** What every completed handshake so far revealed, by device id. Filled by [connect] only. */
    val profiles: StateFlow<Map<String, HandshakeProfile>>

    /** Where peers can reach this node right now - every listening transport's endpoints, merged. */
    val listenerEndpoints: Flow<List<TransportEndpoint>>

    /**
     * The public greeting, over its own connection, which is closed again straight after.
     * Costs no user interaction on either end and leaves nothing behind.
     *
     * **The result is advisory.** Use it to decide which prompt to show, never to decide anything
     * that matters: [connect] re-runs the greeting on its own link, so a lie told here fails there.
     */
    suspend fun probe(peer: PeerRef, policy: ConnectionPolicy? = null): Result<ProbeResult>

    /** Tries the peer's routes in policy order and returns the first greeting that comes back. */
    suspend fun probe(peer: DiscoveredPeer, policy: ConnectionPolicy? = null): Result<ProbeResult>

    /**
     * Opens (or reuses) a session over one specific route.
     *
     * @param request what the user already answered, including which method they were prompted
     * for. A peer that no longer offers that method ends the attempt rather than quietly running
     * something weaker.
     */
    suspend fun connect(
        peer: PeerRef,
        policy: ConnectionPolicy? = null,
        request: AuthRequest? = null,
    ): Result<PeerSession<M>>

    /** Tries the peer's routes in policy order and returns the first session that comes up. */
    suspend fun connect(
        peer: DiscoveredPeer,
        policy: ConnectionPolicy? = null,
        request: AuthRequest? = null,
    ): Result<PeerSession<M>>

    /** Last handshake result for [deviceId], or null if it was never reached in this process. */
    fun profile(deviceId: String): HandshakeProfile?

    suspend fun disconnect(
        deviceId: String,
        reason: CloseReason = CloseReason.Normal,
    )
}
