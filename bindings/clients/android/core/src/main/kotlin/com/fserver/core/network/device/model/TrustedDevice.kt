package com.fserver.core.network.device.model

import com.fserver.core.network.auth.AuthMethod

/**
 * What a completed handshake leaves behind, so the next one with the same device does not have to
 * put a code in front of the user again.
 *
 * One record per key. Everything true of the device rather than of this key - when it was last
 * seen, what it says it is - lives on [metadata], which every key of the same device shares.
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
    /** Shared by every key of [deviceId]. null for a device nothing has recorded yet. */
    val metadata: DeviceMetadata? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrustedDevice) return false

        return publicKey.contentEquals(other.publicKey) &&
                deviceId == other.deviceId &&
                displayName == other.displayName &&
                method == other.method &&
                strength == other.strength &&
                metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + strength.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }

    override fun toString(): String =
        "TrustedDevice(deviceId=$deviceId, displayName=$displayName, method=$method, " +
                "strength=$strength, metadata=$metadata)"
}
