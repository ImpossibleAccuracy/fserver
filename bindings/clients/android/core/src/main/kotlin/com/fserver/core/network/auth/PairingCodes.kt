package com.fserver.core.network.auth

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * One-time codes this device shows so a peer can pair with [AuthMethod.OneTimeCode].
 *
 * At most one code is live. Any attempt spends it, right or wrong, so a mistyped code means
 * issuing a new one rather than trying again.
 */
interface PairingCodes {
    val state: StateFlow<PairingCodeState>

    /** Replaces whatever code was live with a fresh one. */
    fun issue()

    /** Withdraws the live code, if any. */
    fun revoke()
}

sealed interface PairingCodeState {
    /** No code issued, or the last one was withdrawn. */
    data object Idle : PairingCodeState

    data class Active(val code: String, val expiresAt: Instant) : PairingCodeState {
        override fun toString(): String = "Active(code=***, expiresAt=$expiresAt)"
    }

    /** Nobody used the code in time. */
    data object Expired : PairingCodeState

    /** An attempt took the code and has not proven it held the right one - yet, or ever. */
    data object Spent : PairingCodeState

    /** A peer paired with the code. */
    data object Used : PairingCodeState
}
