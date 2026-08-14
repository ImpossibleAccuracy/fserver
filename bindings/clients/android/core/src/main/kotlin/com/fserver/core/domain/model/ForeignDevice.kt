package com.fserver.core.domain.model

import com.fserver.net.connection.PeerRef
import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.identity.PeerIdentity
import java.time.Instant

@ConsistentCopyVisibility
data class ForeignDevice internal constructor(
    val deviceId: String,
    val displayName: String,
    val kind: PeerDescriptor.Kind?,
    val routes: List<PeerRef>,
    val foundBy: DetectionMethod?,
    val lastSeen: Instant,
    val handshake: Handshake?,
    val hasSession: Boolean,
) {
    data class Handshake(
        val identity: PeerIdentity,
        val negotiated: NegotiatedParameters,
    )
}
