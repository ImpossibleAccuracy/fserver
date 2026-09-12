package com.fserver.core.network.device.model

import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.info.model.NetworkInfo
import kotlin.time.Instant

/**
 * What a completed handshake leaves behind, so the next one with the same device does not have to
 * put a code in front of the user again.
 *
 * Equality is by value, with [publicKey] compared by content - otherwise every database emission
 * looks new to a flow and the screen recomposes on each one.
 */
class TrustedDevice(
    val deviceId: String,
    val displayName: String,
    val publicKey: ByteArray,
    /** The strongest method this key has authenticated with, and what it counts for. */
    val method: AuthMethod,
    val strength: String,
    val lastSeen: Instant,
    /** Network the last connection to this device ran over, as [NetworkInfo.id] reports it */
    val lastNetworkId: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrustedDevice) return false

        return publicKey.contentEquals(other.publicKey) &&
                deviceId == other.deviceId &&
                displayName == other.displayName &&
                method == other.method &&
                strength == other.strength &&
                lastSeen == other.lastSeen &&
                lastNetworkId == other.lastNetworkId
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + strength.hashCode()
        result = 31 * result + lastSeen.hashCode()
        result = 31 * result + lastNetworkId.hashCode()
        return result
    }

    override fun toString(): String =
        "TrustedDevice(deviceId=$deviceId, displayName=$displayName, method=$method, " +
                "strength=$strength, lastSeen=$lastSeen, lastNetworkId=$lastNetworkId)"
}
