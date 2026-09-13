package com.fserver.common.model

import java.security.MessageDigest

/**
 * Short, human-comparable form of a public key. What the pairing screen shows and what a QR code
 * carries, so a user can tell "the right device" from "a device".
 *
 * Lives here rather than in `:net` because both halves of the app need the same rendering of the
 * same key: the handshake proves one, and a screen showing a trusted key that was recorded earlier
 * must format it identically or the two read as different devices.
 */
@JvmInline
value class Fingerprint(val value: String) {
    /** The four-character groups on their own, for a UI that lays them out one per cell. */
    val groups: List<String> get() = value.split(" ")

    companion object {
        /** First 8 bytes of SHA-256, in hex, grouped in fours: `9f2c 4a01 b7d3 e820`. */
        fun of(publicKey: ByteArray): Fingerprint {
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)
            val hex = digest.take(8).joinToString("") { "%02x".format(it) }
            return Fingerprint(hex.chunked(4).joinToString(" "))
        }
    }
}
