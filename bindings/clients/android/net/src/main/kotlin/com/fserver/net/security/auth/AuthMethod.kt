package com.fserver.net.security.auth

import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.LocalIdentity
import com.fserver.net.security.trust.AuthStrength

/**
 * One way of proving who is on the other end. The handshake owns the frames; a method owns the
 * cryptography and, when there is one, the string the user is asked about.
 *
 * What a method does **not** own is the part every method has to get right: stating this device's
 * identity, proving possession of its key, checking the peer's proof, and putting the result in
 * front of the trust gate. That is the handshake's work - see [AuthOutcome]. A method reaches a
 * key and a transcript and stops there.
 *
 * Everything a method needs arrives in [AuthContext]; everything the handshake needs comes back in
 * [AuthOutcome]. That is the whole seam - swapping SPAKE2 in later replaces a class here and
 * nothing above it.
 */
interface AuthMethod {
    val id: AuthMethodId

    /**
     * What clearing this method is worth. Pinned with the peer, so a later connection offering
     * less is a downgrade the user gets told about rather than a quiet choice (§6.4).
     */
    val strength: AuthStrength

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
    /** Current device role, initiator or responder. */
    val role: CryptoProvider.Role,

    /** The request the caller made, if any. */
    val request: AuthRequest?,

    /**
     * The hello payloads of *this* connection, initiator's first. Mixed into the derived key so
     * that tampering with what the two sides negotiated in the clear breaks the session instead of
     * quietly succeeding.
     */
    val prologue: ByteArray,

    /** Out-of-band string the transport produced, when it has one. */
    val confirmationCode: String?,

    /**
     * This device. The handshake is what states it to the peer and proves it - a method only needs
     * it to bind it into a transcript.
     */
    val local: LocalIdentity,
)

/**
 * What one method run reached: a key, and enough for the handshake to finish the job.
 *
 * The handshake takes it from here - it seals the identity exchange under [aead], binds the
 * proof of possession to [transcript], puts the proven peer in front of the trust gate with
 * [confirmationCode], and only then runs [confirm].
 */
class AuthOutcome(
    /** Keys the session. Never the same key as [aead]'s: the session restarts nonces at zero. */
    val sharedSecret: suspend () -> ByteArray,

    /** Seals the identity exchange. */
    val aead: CryptoProvider.Aead,

    /** Everything this run depended on, in bytes. */
    val transcript: ByteArray,

    /** The string the two ends are meant to compare, when this method derived one. */
    val confirmationCode: String?,

    /** The last exchange, run once the peer is proven and trusted. */
    val confirm: suspend () -> Unit,
)
