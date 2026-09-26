package com.fserver.net.security.auth.pake

import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.trust.AuthStrength

/**
 * Pairing on this device's long-lived numeric PIN, over [StubPakeExchange].
 *
 * A separate method from [PakeAuthMethod] so the greeting can tell the dialling side to show a
 * number pad. Until the exchange is a real PAKE, one recorded handshake is enough to recover a PIN
 * offline - see [StubPakeExchange].
 */
class PinAuthMethod(
    crypto: CryptoProvider,
    private val loadSavedPin: suspend () -> String,
) : AuthMethod {
    override val id: AuthMethodId = ID

    override val strength: AuthStrength = AuthStrength.SharedSecret

    private val exchange = StubPakeExchange(crypto, namespace = ID.value)

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        val pin = when (context.role) {
            CryptoProvider.Role.Initiator -> context.initiatorParams<PinAuthParams>("PIN").pin
            CryptoProvider.Role.Responder -> loadSavedPin()
        }

        return exchange.run(io, context, pin)
    }

    data class PinAuthParams(
        val pin: String,
    ) : AuthRequest.Params {
        override fun toString(): String = "PinAuthParams(pin=***)"
    }

    companion object {
        val ID: AuthMethodId = AuthMethodId("pin-stub-1")
    }
}
