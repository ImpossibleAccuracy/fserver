package com.fserver.core.network.auth

/**
 * What this device presents to a peer it is dialling - the initiator half.
 *
 * Deliberately not the same type as [OfferedAuthMethod]: that one carries *this* device's own
 * secret, and the two must never be substitutable for one another.
 */
sealed interface AuthCredentials {
    val method: AuthMethod

    data object ConfirmFingerprint : AuthCredentials {
        override val method: AuthMethod = AuthMethod.ConfirmFingerprint
    }

    data object NearbySas : AuthCredentials {
        override val method: AuthMethod = AuthMethod.NearbySas
    }

    /** [password] is what the user typed for *that* peer. */
    data class Password(val password: String) : AuthCredentials {
        override val method: AuthMethod = AuthMethod.Password

        /** Never let a credential print itself: these reach logs and crash dumps. */
        override fun toString(): String = "Password(password=***)"
    }
}
