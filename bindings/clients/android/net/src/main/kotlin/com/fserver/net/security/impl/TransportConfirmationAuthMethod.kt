package com.fserver.net.security.impl

import com.fserver.net.NetworkException
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.identity.PeerIdentityCodec
import com.fserver.net.security.trust.AuthStrength
import java.security.MessageDigest

/**
 * Defers to transport that already encrypted the link and
 * derived a short string from that key exchange (e.g. Nearby's digits).
 */
class TransportConfirmationAuthMethod(
    override val id: AuthMethodId = AuthMethodId.TransportConfirmation,
) : AuthMethod {
    override val requiresChannelSecurity: Boolean = true

    override val strength: AuthStrength = AuthStrength.ChannelBound

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val code = context.confirmationCode
            ?: throw NetworkException.Handshake("$id needs a confirmation code and the transport gave none")

        val peer = PeerIdentityCodec.decode(
            io.exchange(PeerIdentityCodec.encode(context.local))
        )

        context.trust.check(peer, code)

        // Comparing digits is a person's work, so it happens between two auth frames - the peer
        // waits on the auth deadline, not the much shorter handshake one.
        io.exchange(EMPTY)

        return AuthOutcome(
            // The transport keys the link; this only has to be the same on both sides.
            sharedSecret = MessageDigest.getInstance("SHA-256").run {
                update(code.encodeToByteArray())
                update(context.prologue)
                digest()
            },
            peer = peer,
        )
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
