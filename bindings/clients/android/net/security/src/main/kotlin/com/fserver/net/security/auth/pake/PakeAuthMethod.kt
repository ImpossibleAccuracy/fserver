package com.fserver.net.security.auth.pake

import com.fserver.net.NetworkException
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.auth.shared.AuthHelper
import com.fserver.net.security.crypto.CryptoProvider
import io.github.muntashirakon.crypto.spake2.Spake2Context
import io.github.muntashirakon.crypto.spake2.Spake2Role

/**
 * Password-authenticated key exchange (SPAKE2).
 *
 * TODO: Current SPAKE2 library is unsafe for production use:
 * it prevents use of our custom keys, and print all it's own keys to "System.out".
 * It's OK while app in development, but we need to replace it with a proper implementation before release.
 */
class PakeAuthMethod(
    private val crypto: CryptoProvider,
    private val passwordStorage: PakePasswordStorage,
    private val authenticator: PeerAuthenticator?,
) : AuthMethod {
    override val id: AuthMethodId = ID

    override val isEnabled: Boolean
        get() = passwordStorage.isPasswordSet

    init {
        requireNotNull(authenticator) {
            "PakeAuthMethod requires a PeerAuthenticator"
        }
    }

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val rawPassword = when (context.role) {
            CryptoProvider.Role.Initiator -> {
                val request = context.request?.params
                    ?: throw NetworkException.AuthenticationRejected("Missing request/params for PAKE authentication")

                val params = request as PakeAuthParams
                params.password
            }

            CryptoProvider.Role.Responder -> {
                passwordStorage.loadSavedPassword()
            }
        }

        val peerKey = runSpake2Exchange(io, context, rawPassword.encodeToByteArray())
        val secretWithPrologue = AuthHelper.bind(peerKey, context.prologue, BIND_LABEL)
        val aead = crypto.aead(
            AuthHelper.deriveKey(secretWithPrologue, HANDSHAKE_KEY_INFO), context.role
        )

        confirmKey(io, aead)

        val peer = AuthHelper.receivePeerIdentity(context, context.prologue, io, aead)

        val decision = authenticator!!.verify(peer, null)
        if (decision is PeerAuthenticator.Decision.Reject) {
            throw NetworkException.AuthenticationRejected(decision.reason)
        }

        return AuthOutcome(
            sharedSecret = AuthHelper.deriveKey(
                secretWithPrologue,
                SESSION_KEY_INFO,
            ),
            peer = peer,
        )
    }

    /**
     * Runs the SPAKE2 message round trip via [Spake2Context], seeded with [password] and
     * [AuthContext.prologue] so a mismatched negotiation can never land on the same key as matched one.
     */
    private suspend fun runSpake2Exchange(
        io: HandshakeIo,
        context: AuthContext,
        password: ByteArray
    ): ByteArray {
        // Init SPAKE2 context, using current role
        val spake2Context = Spake2Context(
            /* myRole = */
            when (context.role) {
                CryptoProvider.Role.Initiator -> Spake2Role.Alice
                CryptoProvider.Role.Responder -> Spake2Role.Bob
            },
            /* myName = */ context.role.name.toByteArray(),
            /* theirName = */ context.role.reverse().name.toByteArray(),
        )

        // Generate SPAKE2 message from password
        val localMsg = spake2Context.generateMessage(password)

        // Exchange messages
        val response = io.exchange(localMsg)

        // Process the response and derive the shared key
        return try {
            spake2Context.processMessage(response)
        } catch (e: Exception) {
            throw NetworkException.AuthenticationRejected(
                "SPAKE2 key exchange failed",
                e
            )
        }
    }

    /**
     * Explicit key-confirmation round.
     * SPAKE2 does not fail on its own when the password differs - both sides just end up with different keys.
     * So each side proves it holds the same key as the other before anything is trusted on top of it.
     */
    private suspend fun confirmKey(io: HandshakeIo, aead: CryptoProvider.Aead) {
        val receivedFrame = aead.open(io.exchange(aead.seal(CONFIRMED)))
        if (!receivedFrame.contentEquals(CONFIRMED)) {
            throw NetworkException.AuthenticationRejected("key confirmation failed")
        }
    }

    data class PakeAuthParams(
        val password: String,
    ) : AuthRequest.Params

    companion object {
        val ID: AuthMethodId = AuthMethodId("spake2-ver1")

        private val HANDSHAKE_KEY_INFO = "spake2-ver1:handshake".encodeToByteArray()
        private val SESSION_KEY_INFO = "spake2-ver1:session".encodeToByteArray()

        private val BIND_LABEL = "spake2-ver1:bind".encodeToByteArray()

        private val CONFIRMED = "spake2-ver1:confirmed".encodeToByteArray()
    }
}