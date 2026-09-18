package com.fserver.net.support

import com.fserver.common.exception.NetworkException
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.PassthroughCryptoProvider
import com.fserver.common.model.Fingerprint
import com.fserver.net.security.trust.AuthStrength
import java.security.MessageDigest

class TestingAuthMethod(
    private val crypto: CryptoProvider = PassthroughCryptoProvider,
    override val strength: AuthStrength = AuthStrength.UserCompared,
    override val id: AuthMethodId = ID,
    override val requiresChannelSecurity: Boolean = false,
    /** False plays a method with nothing for a person to compare. */
    private val derivesCode: Boolean = true,
    /** Set plays a method pointed at a peer out of band, as a scanned QR does. */
    private val expectedPeer: Fingerprint? = null,
) : AuthMethod {

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val exchange = crypto.newKeyExchange()
        val peerEphemeral = io.exchange(exchange.publicKey)
        val secret = bind(exchange.sharedSecret(peerEphemeral), context.prologue)

        val aead = crypto.aead(secret, context.role)

        return AuthOutcome(
            sharedSecret = { secret },
            aead = aead,
            transcript = context.prologue,
            confirmationCode = pairFingerprint(exchange.publicKey, peerEphemeral)
                .takeIf { derivesCode },
            needVerifyKey = expectedPeer == null,
            verifyPeer = { peer ->
                if (expectedPeer != null && peer.fingerprint != expectedPeer) {
                    throw NetworkException.AuthenticationRejected("not the device that was scanned")
                }
            },
            confirm = { io.exchange(ACCEPTED) },
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