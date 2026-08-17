package com.fserver.net.security.auth.pake

import com.fserver.net.NetworkException
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.auth.shared.AuthHelper
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.trust.AuthStrength
import com.fserver.net.wire.ByteWriter

/**
 * Password-authenticated pairing - placeholder, **not** a real PAKE.
 *
 * A true PAKE leaks nothing about the password to an active attacker. This one only mixes the
 * password into an ephemeral key agreement, so a man-in-the-middle who records one handshake can
 * brute-force the password offline. It exists so the pairing flow above it can be built and tested;
 * replace it with a real SPAKE2/OPAQUE implementation before release.
 *
 * Passive attackers still learn nothing (the key agreement covers that), and a wrong password
 * fails at [confirmKey] rather than silently producing a broken session.
 */
class PakeAuthMethod(
    private val crypto: CryptoProvider,
    private val loadSavedPassword: suspend () -> String,
) : AuthMethod {
    override val id: AuthMethodId = ID

    override val strength: AuthStrength = AuthStrength.SharedSecret

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val rawPassword = when (context.role) {
            CryptoProvider.Role.Initiator -> {
                val request = context.request?.params
                    ?: throw NetworkException.AuthenticationRejected("Missing request/params for PAKE authentication")

                val params = request as PakeAuthParams
                params.password
            }

            CryptoProvider.Role.Responder -> {
                loadSavedPassword()
            }
        }

        val peerKey = runExchange(io, context, rawPassword.encodeToByteArray())
        val secretWithPrologue = AuthHelper.bind(peerKey, context.prologue, BIND_LABEL)
        val aead = crypto.aead(
            AuthHelper.deriveKey(secretWithPrologue, HANDSHAKE_KEY_INFO), context.role
        )

        confirmKey(io, aead)

        val peer = AuthHelper.receivePeerIdentity(context, context.prologue, io, aead)

        // Holding the password is not the same as being trusted: a first contact still surfaces,
        // and a peer already pinned goes through without a prompt.
        context.trust.check(peer, null)

        return AuthOutcome(
            sharedSecret = AuthHelper.deriveKey(
                secretWithPrologue,
                SESSION_KEY_INFO,
            ),
            peer = peer,
        )
    }

    /**
     * One ephemeral key agreement, with the password and both public keys folded into the result.
     * Two ends that disagree on either the password or what they saw on the wire end up with
     * different secrets - which [confirmKey] then catches.
     */
    private suspend fun runExchange(
        io: HandshakeIo,
        context: AuthContext,
        password: ByteArray,
    ): ByteArray {
        val localKeyPair = crypto.newKeyExchange()
        val peerPublicKey = io.exchange(localKeyPair.publicKey)
        val agreed = localKeyPair.sharedSecret(peerPublicKey)

        // Role decides the order, so both ends hash the same bytes.
        val (initiatorKey, responderKey) = when (context.role) {
            CryptoProvider.Role.Initiator -> localKeyPair.publicKey to peerPublicKey
            CryptoProvider.Role.Responder -> peerPublicKey to localKeyPair.publicKey
        }

        val transcript = ByteWriter(password.size + initiatorKey.size + responderKey.size + 16)
            .bytes(password)
            .bytes(initiatorKey)
            .bytes(responderKey)
            .toByteArray()

        return AuthHelper.bind(agreed, transcript, EXCHANGE_LABEL)
    }

    /**
     * Explicit key-confirmation round.
     * The exchange above does not fail on its own when the password differs - both sides just end
     * up with different keys. So each side proves it holds the same key as the other before
     * anything is trusted on top of it.
     */
    private suspend fun confirmKey(io: HandshakeIo, aead: CryptoProvider.Aead) {
        val receivedFrame = try {
            aead.open(io.exchange(aead.seal(CONFIRMED)))
        } catch (e: NetworkException.Protocol) {
            // A wrong password shows up here first: the peer's frame will not open under our key.
            throw NetworkException.AuthenticationRejected("key confirmation failed", e)
        }

        if (!receivedFrame.contentEquals(CONFIRMED)) {
            throw NetworkException.AuthenticationRejected("key confirmation failed")
        }
    }

    data class PakeAuthParams(
        val password: String,
    ) : AuthRequest.Params

    companion object {
        val ID: AuthMethodId = AuthMethodId("pake-stub-1")

        private val HANDSHAKE_KEY_INFO = "pake-stub-1:handshake".encodeToByteArray()
        private val SESSION_KEY_INFO = "pake-stub-1:session".encodeToByteArray()

        private val EXCHANGE_LABEL = "pake-stub-1:exchange".encodeToByteArray()
        private val BIND_LABEL = "pake-stub-1:bind".encodeToByteArray()

        private val CONFIRMED = "pake-stub-1:confirmed".encodeToByteArray()
    }
}
