package com.fserver.net.security.auth.oob

import com.fserver.common.exception.NetworkException
import com.fserver.common.model.Fingerprint
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
 * Pairing where the peer's long-term key was already carried across by hand.
 *
 * The exchange itself is an ephemeral one with nothing secret in it, which is the whole point:
 * what authenticates is the fingerprint the user brought. The handshake's identity exchange makes
 * the peer sign this run's transcript under its long-term key, and then checks what it proved
 * against [AuthOutcome.expectedPeer]. A man in the middle can relay both legs but cannot produce
 * that signature, and cannot reuse the victim's - the transcript it would have to match is the one
 * on the other leg, with the other ephemeral keys in it.
 *
 * Deliberately not Noise `IK`: the static key does not enter a key agreement here, so there is no
 * sender authentication from the DH itself and no identity hiding beyond what the sealed identity
 * frame already gives. The signature over the transcript is what stands in for it, and it is the
 * same proof every other method in this package relies on.
 *
 * Asymmetric by nature. The side that scanned knows which key it expects and is spared a prompt;
 * the side that was scanned knows nothing about the caller and goes through the trust gate as
 * usual. What it is told is that the caller already knows it, so the two prompts do not both fire.
 */
class OutOfBandKeyAuthMethod(
    private val crypto: CryptoProvider,
) : AuthMethod {
    override val id: AuthMethodId = ID

    /**
     * A key read off a screen is a key a person carried between two devices, the same act a short
     * authentication string asks of them - and with no digits to misread.
     */
    override val strength: AuthStrength = AuthStrength.UserCompared

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val expected = (context.request?.params as? Params)?.peerFingerprint
        if (expected == null && context.role == CryptoProvider.Role.Initiator) {
            // Without it this would be a bare key agreement with a prompt at the end: still safe,
            // but no longer the method the user picked, and silently weaker than it looks.
            throw NetworkException.AuthenticationRejected("$id needs the fingerprint that was scanned")
        }

        val local = crypto.newKeyExchange()
        val peerEphemeral = io.exchange(local.publicKey)

        // Role decides the order, so both ends hash the same bytes.
        val (initiatorKey, responderKey) = when (context.role) {
            CryptoProvider.Role.Initiator -> local.publicKey to peerEphemeral
            CryptoProvider.Role.Responder -> peerEphemeral to local.publicKey
        }
        val transcript = ByteWriter(
            context.prologue.size + initiatorKey.size + responderKey.size + TRANSCRIPT_HEADROOM
        )
            .bytes(context.prologue)
            .bytes(initiatorKey)
            .bytes(responderKey)
            .toByteArray()

        val secret = AuthHelper.bind(local.sharedSecret(peerEphemeral), transcript, BIND_LABEL)

        // Handshake and session must never share AEAD keys - see the same note in SasAuthMethod.
        return AuthOutcome(
            sharedSecret = { AuthHelper.deriveKey(secret, SESSION_KEY_INFO) },
            aead = crypto.aead(AuthHelper.deriveKey(secret, HANDSHAKE_KEY_INFO), context.role),
            transcript = transcript,
            confirmationCode = null,
            // Only the side holding a scanned code learned anything off the link; the side that was
            // scanned knows nothing about its caller and is asked about it like any other.
            needVerifyKey = expected == null,
            verifyPeer = { peer ->
                if (expected != null && peer.fingerprint != expected) {
                    throw NetworkException.AuthenticationRejected(
                        "peer proved ${peer.fingerprint.value}, not the ${expected.value} this connection was opened for"
                    )
                }
            },
            // Nothing is left to prove, but the frame is: the side that was scanned may still be
            // asking its user, and this is what waits for that on the auth deadline rather than
            // the handshake one.
            confirm = { io.exchange(EMPTY) },
        )
    }

    /** The fingerprint the access code carried (ToR §3.2). */
    data class Params(val peerFingerprint: Fingerprint) : AuthRequest.Params

    companion object {
        val ID: AuthMethodId = AuthMethodId("oob-key-1")

        private val EMPTY = ByteArray(0)

        private val BIND_LABEL = "oob-key-1:bind".encodeToByteArray()
        private val HANDSHAKE_KEY_INFO = "oob-key-1:handshake".encodeToByteArray()
        private val SESSION_KEY_INFO = "oob-key-1:session".encodeToByteArray()

        private const val TRANSCRIPT_HEADROOM = 16
    }
}
