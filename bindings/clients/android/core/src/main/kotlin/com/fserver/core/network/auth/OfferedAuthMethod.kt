package com.fserver.core.network.auth

/**
 * What this device accepts from peers dialing in:
 * the responder half, and the only place a long-lived local secret lives.
 * See [AuthCredentials] for the other direction.
 */
sealed interface OfferedAuthMethod {
    val method: AuthMethod

    data object ConfirmFingerprint : OfferedAuthMethod {
        override val method: AuthMethod = AuthMethod.ConfirmFingerprint
    }

    /** [password] is *this* device's secret - peers prove they know it. */
    data class Password(val password: String) : OfferedAuthMethod {
        override val method: AuthMethod = AuthMethod.Password

        /** Never let the local secret print itself: these reach logs and crash dumps. */
        override fun toString(): String = "Password(password=***)"
    }
}
