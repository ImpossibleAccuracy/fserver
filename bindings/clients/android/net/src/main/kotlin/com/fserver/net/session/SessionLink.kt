package com.fserver.net.session

import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.SecureChannel

/**
 * One established link under a session: a handshaken, sealed channel. A session outlives its
 * links - a reconnect swaps this out and keeps the [PeerSession] instance.
 */
internal class SessionLink(
    val secure: SecureChannel,
    val negotiated: NegotiatedParameters,
)
