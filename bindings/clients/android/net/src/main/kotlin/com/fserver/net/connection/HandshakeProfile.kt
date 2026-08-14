package com.fserver.net.connection

import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.identity.PeerIdentity

/** What a completed handshake reveals without exchanging a single dictionary message. */
data class HandshakeProfile(
    val identity: PeerIdentity,
    val negotiated: NegotiatedParameters,
    val route: PeerRef,
)
