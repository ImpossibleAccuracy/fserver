package com.fserver.net.security.auth

import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.LocalIdentity
import com.fserver.net.security.identity.PeerIdentity

/**
 * One way of proving who is on the other end. The handshake owns the frames; a method owns the
 * cryptography and, when there is one, the question put to the user.
 *
 * Everything a method needs arrives in [AuthContext]; everything the session needs comes back in
 * [AuthOutcome]. That is the whole seam - swapping SPAKE2 in later replaces a class here and
 * nothing above it.
 */
interface AuthMethod {
    val id: AuthMethodId

    /**
     * True when this method leans on protection the transport provides and means nothing without it.
     * Such a method is never offered on transport that does not declare it - see [com.fserver.net.spi.ChannelSecurity].
     */
    val requiresChannelSecurity: Boolean get() = false

    suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome
}

/**
 * How a method talks to the peer. Deliberately narrow: a method sends and receives payloads and
 * never learns that they travel as frames, so the wire format can change under it.
 *
 * The handshake enforces the round limit and the deadline, not the method.
 */
interface HandshakeIo {
    suspend fun send(payload: ByteArray)

    suspend fun receive(): ByteArray

    /**
     * Send, then wait. Safe to call at the same moment on both ends - the sends are buffered, so
     * two peers that both start with [exchange] do not deadlock.
     */
    suspend fun exchange(payload: ByteArray): ByteArray {
        send(payload)
        return receive()
    }
}

class AuthContext(
    val role: CryptoProvider.Role,
    /**
     * The hello payloads of *this* connection, initiator's first. Mixed into the derived key so
     * that tampering with what the two sides negotiated in the clear breaks the session instead of
     * quietly succeeding.
     */
    val prologue: ByteArray,
    /** Out-of-band string the transport produced, when it has one. */
    val confirmationCode: String?,
    /**
     * This device. Nothing above states who this is any more - the public hello carries no
     * identity at all - so a method sends this itself, and is the reason the peer's answer counts
     * for something.
     */
    val local: LocalIdentity,
)

class AuthOutcome(
    /** Keys the session. */
    val sharedSecret: ByteArray,
    /** The peer this method is willing to vouch for. */
    val peer: PeerIdentity,
)
