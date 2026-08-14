package com.fserver.net.security.auth

import com.fserver.net.NetworkException
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.identity.PeerIdentityCodec
import java.security.MessageDigest

/**
 * Defers to a transport that already encrypted the link and derived a short string from that key
 * exchange - Nearby's digits. The user comparing them on both screens is a real short
 * authentication string, so this is delegation rather than a placeholder.
 *
 * Identities go across unsealed here, which is only acceptable because this method is offered
 * solely on a transport declaring [com.fserver.net.spi.ChannelSecurity.Sas] - that is, one that
 * has already encrypted the link.
 *
 * Two things it does not give. The channel is authenticated, the *device* is not: there is no
 * proof tying the key below to the party that compared digits, which is why the key still has to
 * be pinned by the host. And the verdict must never outlive the connection it was given for -
 * only a pinned key may be cached.
 */
class SasAuthMethod(
    private val authenticator: PeerAuthenticator?,
    override val id: AuthMethodId = AuthMethodId.NEARBY_SAS,
) : AuthMethod {
    override val requiresChannelSecurity: Boolean = true

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val code = context.confirmationCode
            ?: throw NetworkException.Handshake("$id needs a confirmation code and the transport gave none")

        val peer = PeerIdentityCodec.decode(
            io.exchange(PeerIdentityCodec.encode(context.local))
        )

        val verdict = authenticator?.verify(peer, code) ?: PeerAuthenticator.Decision.Trust
        if (verdict is PeerAuthenticator.Decision.Reject) {
            throw NetworkException.AuthenticationRejected(verdict.reason)
        }

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
