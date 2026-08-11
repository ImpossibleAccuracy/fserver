package com.fserver.net.connection

import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.PeerIdentity
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** One way to reach one device. A device found twice has two of these and still one session. */
data class PeerRef(
    val deviceId: String,
    val transport: SpiId,
    val endpoint: TransportEndpoint,
)

/**
 * The only component that knows about every transport at once: it picks a route, runs the
 * handshake, keeps one session per device, and gates what arrives.
 */
interface ConnectionManager<M : Any> {
    val sessions: StateFlow<List<PeerSession<M>>>

    /**
     * Connection attempts from other devices. Nothing is accepted until someone calls
     * [IncomingRequest.accept] - auto-accepting hands any device in radio range a channel into
     * the app.
     */
    val incoming: Flow<IncomingRequest>

    /** Opens (or reuses) a session over one specific route. */
    suspend fun connect(peer: PeerRef, policy: ConnectionPolicy? = null): Result<PeerSession<M>>

    /** Tries the peer's routes in policy order and returns the first session that comes up. */
    suspend fun connect(
        peer: DiscoveredPeer,
        policy: ConnectionPolicy? = null
    ): Result<PeerSession<M>>

    /** Find device by ID, or null if it is not connected. */
    fun session(deviceId: String): PeerSession<M>?

    suspend fun disconnect(
        deviceId: String,
        reason: CloseReason = CloseReason.Normal,
    )

    /** Handshakes, reads what came back, and hangs up. */
    suspend fun probe(peer: PeerRef, policy: ConnectionPolicy? = null): Result<Profile>

    /** What a handshake reveals without exchanging a single dictionary message. */
    data class Profile(
        val identity: PeerIdentity,
        val negotiated: NegotiatedParameters,
        val route: PeerRef,
    )

    interface IncomingRequest {
        val transport: SpiId
        val peer: DiscoveredEndpoint

        /** Digits or code the user must compare, when the transport provides one. */
        val confirmationCode: String?

        /** On success the session shows up in [ConnectionManager.sessions]. */
        suspend fun accept(): Result<Unit>

        suspend fun reject(reason: CloseReason = CloseReason.RejectedByUser)
    }
}
