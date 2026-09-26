package com.fserver.core.network.auth

import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.pake.OneTimeCodeAuthMethod
import com.fserver.net.security.auth.pake.PakeAuthMethod
import com.fserver.net.security.auth.pake.PinAuthMethod
import com.fserver.net.security.auth.sas.SasAuthMethod

/**
 * Available authentication methods. Names a method, and nothing else: what a peer must *present*
 * is [AuthCredentials], what this device *accepts* is [OfferedAuthMethod].
 */
enum class AuthMethod(
    internal val authMethodId: AuthMethodId,
) {
    /** Ephemeral key agreement, confirmed by comparing a fingerprint on both screens. */
    ConfirmFingerprint(SasAuthMethod.ID),

    /** Nearby's own channel security, confirmed by comparing a short on-screen code. */
    NearbySas(AuthMethodId.TransportConfirmation),

    /** Password-authenticated key exchange. */
    Password(PakeAuthMethod.ID),

    /** This device's long-lived numeric PIN, over the same exchange as [Password]. */
    Pin(PinAuthMethod.ID),

    /** A short code this device shows on screen, spent by the first attempt. See [PairingCodes]. */
    OneTimeCode(OneTimeCodeAuthMethod.ID);

    companion object {
        internal fun fromId(methodId: AuthMethodId): AuthMethod? =
            entries.find { it.authMethodId == methodId }
    }
}
