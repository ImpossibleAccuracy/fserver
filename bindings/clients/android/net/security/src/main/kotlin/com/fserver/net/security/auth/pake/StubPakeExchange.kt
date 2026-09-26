package com.fserver.net.security.auth.pake

import com.fserver.common.exception.NetworkException
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.auth.shared.AuthHelper
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.wire.ByteWriter

/**
 * Secret-bound key agreement shared by the secret-based methods - placeholder, **not** a real PAKE.
 *
 * A true PAKE leaks nothing about the secret to an active attacker. This one only mixes the
 * secret into an ephemeral key agreement, so a man-in-the-middle who records one handshake can
 * brute-force the secret offline. It exists so the pairing flows above it can be built and tested;
 * replace it with a real SPAKE2/CPace implementation before release.
 *
 * The user is not asked about a first-contact peer - see [AuthOutcome.needVerifyKey]; a changed
 * key or a downgrade still is.
 *
 * Passive attackers still learn nothing (the key agreement covers that), and a wrong secret
 * fails rather than silently producing a broken session: the handshake's identity exchange is
 * sealed under the derived key and will not open, and [confirmKey] catches the case where it did.
 *
 * [namespace] keeps each method's keys apart, so one method's transcript never keys another's.
 */
internal class StubPakeExchange(
    private val crypto: CryptoProvider,
    namespace: String,
) {
    private val handshakeKeyInfo = "$namespace:handshake".encodeToByteArray()
    private val sessionKeyInfo = "$namespace:session".encodeToByteArray()
    private val exchangeLabel = "$namespace:exchange".encodeToByteArray()
    private val bindLabel = "$namespace:bind".encodeToByteArray()
    private val confirmed = "$namespace:confirmed".encodeToByteArray()

    /** [onConfirmed] runs once both ends proved they hold the same [secret]. */
    suspend fun run(
        io: HandshakeIo,
        context: AuthContext,
        secret: String,
        onConfirmed: () -> Unit = {},
    ): AuthOutcome {
        val peerKey = runExchange(io, context, secret.encodeToByteArray())
        val secretWithPrologue = AuthHelper.bind(peerKey, context.prologue, bindLabel)
        val aead = crypto.aead(
            AuthHelper.deriveKey(secretWithPrologue, handshakeKeyInfo), context.role
        )

        // Holding the secret is not the same as being trusted, so nothing here decides that:
        // the handshake still puts the proven peer in front of the gate.
        return AuthOutcome(
            sharedSecret = { AuthHelper.deriveKey(secretWithPrologue, sessionKeyInfo) },
            aead = aead,
            transcript = context.prologue,
            confirmationCode = null,
            // The secret is the user's verdict: they gave it to both ends, and the identity
            // exchange only opens under a key derived from it. Nothing left to compare on screen.
            needVerifyKey = false,
            confirm = {
                confirmKey(io, aead)
                onConfirmed()
            },
        )
    }

    /**
     * One ephemeral key agreement, with the secret and both public keys folded into the result.
     * Two ends that disagree on either the secret or what they saw on the wire end up with
     * different keys - which [confirmKey] then catches.
     */
    private suspend fun runExchange(
        io: HandshakeIo,
        context: AuthContext,
        secret: ByteArray,
    ): ByteArray {
        val localKeyPair = crypto.newKeyExchange()
        val peerPublicKey = io.exchange(localKeyPair.publicKey)
        val agreed = localKeyPair.sharedSecret(peerPublicKey)

        // Role decides the order, so both ends hash the same bytes.
        val (initiatorKey, responderKey) = when (context.role) {
            CryptoProvider.Role.Initiator -> localKeyPair.publicKey to peerPublicKey
            CryptoProvider.Role.Responder -> peerPublicKey to localKeyPair.publicKey
        }

        val transcript = ByteWriter(secret.size + initiatorKey.size + responderKey.size + 16)
            .bytes(secret)
            .bytes(initiatorKey)
            .bytes(responderKey)
            .toByteArray()

        return AuthHelper.bind(agreed, transcript, exchangeLabel)
    }

    /**
     * Explicit key-confirmation round.
     * The exchange above does not fail on its own when the secret differs - both sides just end
     * up with different keys, and nothing before this proves the two agree in that direction.
     */
    private suspend fun confirmKey(io: HandshakeIo, aead: CryptoProvider.Aead) {
        val receivedFrame = try {
            aead.open(io.exchange(aead.seal(confirmed)))
        } catch (e: NetworkException.Protocol) {
            // A wrong secret shows up here first: the peer's frame will not open under our key.
            throw NetworkException.AuthenticationRejected("key confirmation failed", e)
        }

        if (!receivedFrame.contentEquals(confirmed)) {
            throw NetworkException.AuthenticationRejected("key confirmation failed")
        }
    }
}

/** The secret the initiator was given, or a rejection when the caller supplied none. */
internal inline fun <reified P> AuthContext.initiatorParams(methodName: String): P =
    request?.params as? P
        ?: throw NetworkException.AuthenticationRejected("Missing request/params for $methodName authentication")
