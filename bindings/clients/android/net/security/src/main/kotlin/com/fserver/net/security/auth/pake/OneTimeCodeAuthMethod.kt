package com.fserver.net.security.auth.pake

import com.fserver.common.exception.NetworkException
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.trust.AuthStrength

/**
 * Pairing on a short code this device shows on its screen, over [StubPakeExchange].
 *
 * The code is spent by the first attempt, right or wrong. That is what keeps it safe on the stub
 * exchange: a failed attempt hands the attacker a transcript to brute-force offline, and what they
 * recover is a code no longer accepted.
 */
class OneTimeCodeAuthMethod(
    crypto: CryptoProvider,
    private val codes: OneTimeCodeSource,
) : AuthMethod {
    override val id: AuthMethodId = ID

    override val strength: AuthStrength = AuthStrength.SharedSecret

    private val exchange = StubPakeExchange(crypto, namespace = ID.value)

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome =
        when (context.role) {
            CryptoProvider.Role.Initiator -> exchange.run(
                io = io,
                context = context,
                secret = context.initiatorParams<OneTimeCodeParams>("one-time code").code,
            )

            CryptoProvider.Role.Responder -> {
                val code = codes.take()
                    ?: throw NetworkException.AuthenticationRejected("no one-time code is active")

                exchange.run(io, context, code, onConfirmed = codes::onUsed)
            }
        }

    data class OneTimeCodeParams(
        val code: String,
    ) : AuthRequest.Params {
        override fun toString(): String = "OneTimeCodeParams(code=***)"
    }

    companion object {
        val ID: AuthMethodId = AuthMethodId("otp-stub-1")
    }
}

/** Where the responder gets the code it is currently showing. */
interface OneTimeCodeSource {
    /** The active code, withdrawn so no second attempt can use it; null when none is active. */
    suspend fun take(): String?

    /** A peer proved it held the code [take] last handed out. */
    fun onUsed() {}
}
