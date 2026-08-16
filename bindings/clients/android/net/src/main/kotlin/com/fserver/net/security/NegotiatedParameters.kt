package com.fserver.net.security

import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.PeerIdentity

/** What the two ends agreed on. Shown by the pairing UI, and the reason a session is a session. */
data class NegotiatedParameters(
    val protocolVersion: Int,
    val dictionaryVersion: Int,
    val cipherSuite: CryptoProvider.Suite,
    val maxFrameSize: Int,
    val peer: PeerIdentity,
    val peerDescriptor: PeerDescriptor,
    /** Which method authenticated this session. */
    val authMethodId: AuthMethodId,
)
