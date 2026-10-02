package com.fserver.core.crypto.spi

import javax.crypto.SecretKey

/**
 * An AEAD that seals one segment of a file at rest. Format, nonces, segment order and truncation
 * are the core's job: [aad] already binds the segment to its file and place.
 */
interface StorageCipher {
    /** Written into every file header and looked up on read, so never reuse one for another algorithm. */
    val id: String

    /** Bytes of the fresh random nonce the core hands to every [seal]. */
    val nonceSize: Int

    /** What [seal] adds: its output is always `plain.size + tagSize` bytes. */
    val tagSize: Int

    fun seal(key: SecretKey, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray

    /** Throws when [sealed], [nonce] or [aad] are not what [seal] was given. */
    fun open(key: SecretKey, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray
}
