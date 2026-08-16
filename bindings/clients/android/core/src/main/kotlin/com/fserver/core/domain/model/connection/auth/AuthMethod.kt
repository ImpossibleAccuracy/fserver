package com.fserver.core.domain.model.connection.auth

import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.pake.PakeAuthMethod
import com.fserver.net.security.auth.sas.SasAuthMethod

/**
 * Available authentication methods.
 */
enum class AuthMethod(
    internal val authMethodId: AuthMethodId,
) {
    /** Ephemeral key agreement, confirmed by comparing a fingerprint on both screens. */
    ConfirmFingerprint(SasAuthMethod.ID),

    /** Nearby's own channel security, confirmed by comparing a short on-screen code. */
    NearbySas(AuthMethodId.TransportConfirmation),

    /** Password-authenticated key exchange. */
    Password(PakeAuthMethod.ID);

    companion object {
        internal fun fromId(methodId: AuthMethodId): AuthMethod? =
            AuthMethod.entries.find { it.authMethodId == methodId }
    }
}
