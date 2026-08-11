package com.fserver.net.security

/** What the two ends agreed on. Shown by the pairing UI, and the reason a session is a session. */
data class NegotiatedParameters(
    val protocolVersion: Int,
    val dictionaryVersion: Int,
    val cipherSuite: CryptoProvider.Suite,
    val maxFrameSize: Int,
    val peer: PeerIdentity,
)
