package com.fserver.net.security.auth

import com.fserver.net.NetworkException
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.Fingerprint
import com.fserver.net.security.identity.PeerIdentityCodec
import java.security.MessageDigest

/**
 * Key agreement, then a sealed exchange of identities, then whatever the host asks the user. The
 * default on transports that protect nothing themselves, and what the pairing dialog runs behind.
 *
 * The order is the point. Ephemeral keys go first and say nothing about the device; only once
 * there is a key do the two sides tell each other who they are. That is what keeps a device id off
 * the wire for anyone who merely dialled the address.
 *
 * It proves no more than the user's answer does: a null [com.fserver.net.security.PeerAuthenticator] trusts everyone, which
 * is right for a test rig and wrong for a shipping client. Replacing this with a real PAKE is the
 * point of the [AuthMethod] seam.
 */
class ConfirmAuthMethod(
    private val crypto: CryptoProvider,
    private val authenticator: PeerAuthenticator?,
) : AuthMethod {
    override val id: AuthMethodId = ID

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val exchange = crypto.newKeyExchange()
        val peerEphemeral = io.exchange(exchange.publicKey)
        val secret = bind(exchange.sharedSecret(peerEphemeral), context.prologue)

        val aead = crypto.aead(secret, context.role)
        val peer = PeerIdentityCodec.decode(
            aead.open(io.exchange(aead.seal(PeerIdentityCodec.encode(context.local))))
        )

        // just scaffold: no PAKE yet, so the "code" is a fingerprint of both identity keys, sorted
        // so role (initiator/responder) does not change which side sees which half first - both
        // screens end up with the same string to compare.
        val code = pairFingerprint(context.local.publicKey, peer.publicKey)

        val verdict = authenticator?.verify(peer, code)
            ?: PeerAuthenticator.Decision.Trust
        if (verdict is PeerAuthenticator.Decision.Reject) {
            throw NetworkException.AuthenticationRejected(verdict.reason)
        }

        // A last round on purpose. Deciding is where a person is involved, so it has to happen
        // *between* two auth frames: the peer then waits on the generous auth deadline rather than
        // on the handshake one, and someone who hesitates does not lose the connection.
        io.exchange(ACCEPTED)

        return AuthOutcome(
            sharedSecret = secret,
            // Unproven: the key was sent under a channel nobody authenticated, so this says
            // "whoever answered claims to be that" and no more. A real handshake pattern is what
            // turns it into a proof.
            peer = peer,
        )
    }

    /**
     * Folds the cleartext negotiation into the key. Both ends hash the same bytes, so a peer that
     * saw a different hello than the one actually sent derives a different key and the session
     * simply fails to come up - no separate comparison to forget.
     */
    private fun bind(secret: ByteArray, prologue: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(secret)
            update(prologue)
            digest()
        }

    private fun pairFingerprint(a: ByteArray, b: ByteArray): String {
        val (first, second) = if (compareUnsigned(a, b) <= 0) a to b else b to a
        return Fingerprint.of(first + second).value
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val cmp = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size - b.size
    }

    companion object {
        val ID: AuthMethodId = AuthMethodId("confirm-dh")
        private val ACCEPTED = ByteArray(0)
    }
}
