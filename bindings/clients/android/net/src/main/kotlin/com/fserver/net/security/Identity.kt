package com.fserver.net.security

import com.fserver.net.peer.PeerDescriptor
import java.security.MessageDigest

/**
 * Short, human-comparable form of a public key. What the pairing screen shows and what a QR code
 * carries, so a user can tell "the right device" from "a device".
 */
@JvmInline
value class Fingerprint(val value: String) {
    companion object {
        /** First 8 bytes of SHA-256, in hex, grouped in fours: `9f2c 4a01 b7d3 e820`. */
        fun of(publicKey: ByteArray): Fingerprint {
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)
            val hex = digest.take(8).joinToString("") { "%02x".format(it) }
            return Fingerprint(hex.chunked(4).joinToString(" "))
        }
    }
}

/**
 * This device, as other devices see it.
 */
data class LocalIdentity(
    val deviceId: String,
    val displayName: String,
    val publicKey: ByteArray,
    val kind: PeerDescriptor.Kind? = null,
    val accessMode: PeerDescriptor.AccessMode? = null,
) {
    val fingerprint: Fingerprint = Fingerprint.of(publicKey)

    override fun equals(other: Any?): Boolean = other is LocalIdentity &&
            deviceId == other.deviceId &&
            displayName == other.displayName &&
            publicKey.contentEquals(other.publicKey) &&
            kind == other.kind &&
            accessMode == other.accessMode

    override fun hashCode(): Int {
        var result = (deviceId.hashCode() * 31 + displayName.hashCode()) * 31 + publicKey.contentHashCode()
        result = result * 31 + kind.hashCode()
        result = result * 31 + accessMode.hashCode()
        return result
    }
}

/** The device on the other end, as it described itself during the handshake. */
data class PeerIdentity(
    val deviceId: String,
    val displayName: String,
    val publicKey: ByteArray,
) {
    val fingerprint: Fingerprint = Fingerprint.of(publicKey)

    override fun equals(other: Any?): Boolean = other is PeerIdentity &&
            deviceId == other.deviceId &&
            displayName == other.displayName &&
            publicKey.contentEquals(other.publicKey)

    override fun hashCode(): Int =
        (deviceId.hashCode() * 31 + displayName.hashCode()) * 31 + publicKey.contentHashCode()
}
