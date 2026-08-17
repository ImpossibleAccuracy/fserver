package com.fserver.net.handshake

import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.security.trust.AuthStrength
import java.security.MessageDigest

/**
 * Keys the session off the prologue and nothing else, so that whether the two sides agree is
 * exactly whether they saw the same hellos. [tampered] plays the side that did not.
 */
class PrologueBound(private val tampered: Boolean = false) : AuthMethod {
    override val id = AuthMethodId("prologue-bound")
    override val strength = AuthStrength.UserCompared

    override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome {
        io.exchange(ByteArray(0))
        val prologue = context.prologue.copyOf().also { if (tampered) it[0]++ }
        return AuthOutcome(
            sharedSecret = MessageDigest.getInstance("SHA-256").digest(prologue),
            peer = PeerIdentity(context.local.deviceId, context.local.publicKey),
        )
    }
}