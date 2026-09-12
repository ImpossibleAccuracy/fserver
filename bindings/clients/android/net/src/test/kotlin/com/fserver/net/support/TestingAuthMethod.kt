package com.fserver.net.support

import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.PassthroughCryptoProvider
import com.fserver.net.security.identity.Fingerprint
import com.fserver.net.security.identity.PeerIdentityCodec
import com.fserver.net.security.trust.AuthStrength
import java.security.MessageDigest

class TestingAuthMethod(
    private val crypto: CryptoProvider = PassthroughCryptoProvider,
    override val strength: AuthStrength = AuthStrength.UserCompared,
    override val id: AuthMethodId = ID,
    override val requiresChannelSecurity: Boolean = false,
    /** Plays a method that forgets the gate, so the handshake's own check is visible. */
    private val skipTrust: Boolean = false,
) : AuthMethod {

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val exchange = crypto.newKeyExchange()
        val peerEphemeral = io.exchange(exchange.publicKey)
        val secret = bind(exchange.sharedSecret(peerEphemeral), context.prologue)

        val aead = crypto.aead(secret, context.role)
        val peer = PeerIdentityCodec.decode(
            aead.open(io.exchange(aead.seal(PeerIdentityCodec.encode(context.local))))
        )

        val code = pairFingerprint(context.local.publicKey, peer.publicKey)

        if (!skipTrust) context.trust.check(peer, code)

        io.exchange(ACCEPTED)

        return AuthOutcome(
            sharedSecret = secret,
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