package com.fserver.net.security.trust

import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.identity.Fingerprint

/**
 * What a completed handshake leaves behind, so the next one with the same device does not have to
 * put a code in front of the user again.
 */
data class TrustRecord(
    val publicKey: ByteArray,
    val deviceId: String,
    val displayName: String,
    /** The strongest method this key has authenticated with, and what it counts for. */
    val method: AuthMethodId,
    val strength: AuthStrength,
) {
    val fingerprint: Fingerprint get() = Fingerprint.of(publicKey)

    override fun equals(other: Any?): Boolean = other is TrustRecord &&
            publicKey.contentEquals(other.publicKey) &&
            deviceId == other.deviceId &&
            displayName == other.displayName &&
            method == other.method &&
            strength == other.strength

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = result * 31 + deviceId.hashCode()
        result = result * 31 + displayName.hashCode()
        result = result * 31 + method.hashCode()
        return result * 31 + strength.hashCode()
    }
}
