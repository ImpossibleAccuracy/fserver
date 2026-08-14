package com.fserver.net.connection

import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.SpiId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow


/**
 * Component to manage incoming connections from other devices.
 */
interface IncomingConnectionsManager<M : Any> {
    /** Active sessions with peers that have been accepted. */
    val sessions: StateFlow<List<PeerSession<M>>>

    /**
     * Peers that have got past the public greeting and asked to authenticate.
     * Device that only probed never appears here - nothing is put in front of the user for a question.
     *
     * Every inbound connection is answered and taken through the public
     * greeting before anything reaches here.
     * A peer that only wanted the greeting hangs up at that point and never becomes request,
     * which is what keeps a probe from putting a question in front of the user.
     */
    val incoming: Flow<IncomingRequest>

    /** Find device by ID, or null if it is not connected. */
    fun session(deviceId: String): PeerSession<M>?

    interface IncomingRequest {
        val transport: SpiId
        val peer: DiscoveredEndpoint
        val confirmationCode: String?

        /** On success the session shows up in [sessions]. */
        suspend fun accept(): Result<Unit>

        suspend fun reject(reason: CloseReason = CloseReason.RejectedByUser)
    }
}
