package com.fserver.net.security.auth.transport

import com.fserver.common.exception.NetworkException
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.auth.shared.AuthHelper
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.trust.AuthStrength
import com.fserver.net.wire.ByteWriter

/**
 * Defers to a transport that already encrypted the link and
 * derived a short string from that key exchange (e.g. Nearby's digits).
 *
 * The identity still travels signed. The transport proves the channel and never the device, so a
 * peer that only reached the other end of the link could otherwise replay a public key it has seen
 * before - a public key is not a secret - and land on that key's pin without a prompt.
 */
class TransportConfirmationAuthMethod(
    private val crypto: CryptoProvider,
    override val id: AuthMethodId = AuthMethodId.TransportConfirmation,
) : AuthMethod {
    override val requiresChannelSecurity: Boolean = true

    override val strength: AuthStrength = AuthStrength.ChannelBound

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val code = context.confirmationCode
            ?: throw NetworkException.Handshake("$id needs a confirmation code and the transport gave none")

        // The transport keys the link; this only has to be the same on both sides.
        val secret = AuthHelper.bind(code.encodeToByteArray(), context.prologue, BIND_LABEL)

        // What both ends sign over: the cleartext negotiation plus the code the transport derived.
        val transcript = ByteWriter(context.prologue.size + code.length + TRANSCRIPT_HEADROOM)
            .bytes(context.prologue)
            .string(code)
            .toByteArray()

        // Handshake and session must never share AEAD keys - see the same note in SasAuthMethod.
        val aead = crypto.aead(AuthHelper.deriveKey(secret, HANDSHAKE_KEY_INFO), context.role)
        val peer = AuthHelper.receivePeerIdentity(context, transcript, io, aead)

        context.trust.check(peer, code)

        // Comparing digits is a person's work, so it happens between two auth frames - the peer
        // waits on the auth deadline, not the much shorter handshake one.
        io.exchange(EMPTY)

        return AuthOutcome(
            sharedSecret = AuthHelper.deriveKey(secret, SESSION_KEY_INFO),
            peer = peer,
        )
    }

    private companion object {
        val EMPTY = ByteArray(0)

        val BIND_LABEL = "transport-confirmation:bind".encodeToByteArray()
        val HANDSHAKE_KEY_INFO = "transport-confirmation:handshake".encodeToByteArray()
        val SESSION_KEY_INFO = "transport-confirmation:session".encodeToByteArray()

        const val TRANSCRIPT_HEADROOM = 16
    }
}
