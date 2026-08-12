package com.fserver.core.domain.model

import com.fserver.net.connection.PeerRef
import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.PeerIdentity
import java.time.Instant

@ConsistentCopyVisibility
data class ForeignDevice internal constructor(
    val descriptor: PeerDescriptor,
    val routes: List<PeerRef>,
    val foundBy: DetectionMethod?,
    val lastSeen: Instant,
    val handshake: Handshake?,
) {
    val hasSession: Boolean get() = handshake != null

    data class Handshake(
        val identity: PeerIdentity,
        val negotiated: NegotiatedParameters,
    )
}
