package com.fserver.net.security.identity

import com.fserver.common.model.Fingerprint
import com.fserver.net.peer.PeerDescriptor

/**
 * This device, as other devices see it.
 */
data class LocalIdentity(
    val deviceId: String,
    val displayName: String,
    val publicKey: ByteArray,
    val kind: String? = null,
) {
    val fingerprint: Fingerprint = Fingerprint.of(publicKey)

    override fun equals(other: Any?): Boolean = other is LocalIdentity &&
            deviceId == other.deviceId &&
            displayName == other.displayName &&
            publicKey.contentEquals(other.publicKey) &&
            kind == other.kind

    override fun hashCode(): Int {
        var result = (deviceId.hashCode() * 31 + displayName.hashCode()) * 31 + publicKey.contentHashCode()
        result = result * 31 + kind.hashCode()
        return result
    }
}

/**
 * The device on the other end, reduced to what the handshake proves. A name is never identity - it
 * is descriptor data, and lives in [PeerDescriptor].
 */
data class PeerIdentity(
    val deviceId: String,
    val publicKey: ByteArray,
) {
    val fingerprint: Fingerprint = Fingerprint.of(publicKey)

    override fun equals(other: Any?): Boolean = other is PeerIdentity &&
            deviceId == other.deviceId &&
            publicKey.contentEquals(other.publicKey)

    override fun hashCode(): Int = deviceId.hashCode() * 31 + publicKey.contentHashCode()
}
