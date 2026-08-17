package com.fserver.net.security

import com.fserver.net.security.trust.TrustPrompt

/**
 * The trust gate. Only the host can run it - it is the one holding a screen on which a user can
 * compare a fingerprint or a pair of digits.
 *
 * Optional: leaving it out of the config means every peer that completes a handshake is trusted,
 * which is fine for a test rig and wrong for a shipping client.
 */
fun interface PeerAuthenticator {
    suspend fun verify(prompt: TrustPrompt): Decision

    sealed interface Decision {
        data object Trust : Decision
        data class Reject(val reason: String) : Decision
    }
}
