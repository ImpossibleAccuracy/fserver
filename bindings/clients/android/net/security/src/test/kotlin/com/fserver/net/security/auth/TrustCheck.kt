package com.fserver.net.security.auth

import com.fserver.net.security.identity.PeerIdentity

/** What the handshake does with a proven peer, as far as these tests care. */
internal typealias TrustCheck = suspend (PeerIdentity, String?) -> Unit
