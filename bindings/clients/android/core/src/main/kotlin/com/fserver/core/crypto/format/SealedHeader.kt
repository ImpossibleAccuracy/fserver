package com.fserver.core.crypto.format

import com.fserver.common.exception.FileSystemException
import com.fserver.core.crypto.format.SealedHeader.Companion.decode
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/**
 * What a sealed file says about itself. [encoded] goes into every segment's AAD, so a changed
 * header fails the first segment opened, and [salt] keeps segments from moving between files.
 *
 * `magic | version | segmentSize i32 | nonceSize u8 | tagSize u8 | cipherId | keyId | salt`,
 * both ids as a u8 length plus UTF-8.
 */
internal class SealedHeader(
    val segmentSize: Int,
    val nonceSize: Int,
    val tagSize: Int,
    val cipherId: String,
    val keyId: String,
    val salt: ByteArray,
) {
    val encoded: ByteArray = encode()

    val layout = SealedLayout(
        headerSize = encoded.size,
        segmentSize = segmentSize,
        overhead = nonceSize + tagSize,
    )

    init {
        require(segmentSize in MinSegmentSize..MaxSegmentSize) { "Segment size out of range: $segmentSize" }
        require(nonceSize in 1..255 && tagSize in 1..255) { "Nonce or tag size out of range" }
        require(salt.size == SaltSize) { "Salt must be $SaltSize bytes" }
    }

    private fun encode(): ByteArray {
        val cipher = cipherId.toByteArray(Charsets.UTF_8)
        val key = keyId.toByteArray(Charsets.UTF_8)
        require(cipher.size in 1..255 && key.size in 1..255) { "Cipher and key ids must be 1-255 bytes" }

        return ByteBuffer.allocate(FixedSize + cipher.size + key.size)
            .put(Magic)
            .put(Version)
            .putInt(segmentSize)
            .put(nonceSize.toByte())
            .put(tagSize.toByte())
            .put(cipher.size.toByte()).put(cipher)
            .put(key.size.toByte()).put(key)
            .put(salt)
            .array()
    }

    companion object {
        const val DefaultSegmentSize = 64 * 1024
        const val SaltSize = 16

        /** Enough for any header: read this much from offset 0 and hand it to [decode]. */
        const val MaxSize = 4 + 1 + 4 + 1 + 1 + (1 + 255) * 2 + SaltSize

        private const val MinSegmentSize = 1024
        private const val MaxSegmentSize = 4 * 1024 * 1024
        private const val FixedSize = 4 + 1 + 4 + 1 + 1 + 1 + 1 + SaltSize
        private const val Version: Byte = 1
        private val Magic =
            byteArrayOf('F'.code.toByte(), 'S'.code.toByte(), 'E'.code.toByte(), 'C'.code.toByte())

        /** How long the header of a file sealed by [cipherId] under [keyId] is. */
        fun sizeOf(cipherId: String, keyId: String): Int =
            FixedSize + cipherId.toByteArray(Charsets.UTF_8).size + keyId.toByteArray(Charsets.UTF_8).size

        /** True when [bytes] start like a sealed file - a hint only, see Storage Encryption §6.1. */
        fun hasMagic(bytes: ByteArray, length: Int = bytes.size): Boolean =
            length >= Magic.size && Magic.indices.all { bytes[it] == Magic[it] }

        /** The header [bytes] start with; [length] of them are valid. */
        fun decode(bytes: ByteArray, length: Int = bytes.size): SealedHeader {
            if (!hasMagic(bytes, length)) throw FileSystemException.Corrupted("no sealed header")

            val buffer = ByteBuffer.wrap(bytes, 0, length)
            buffer.position(Magic.size)
            return try {
                val version = buffer.get()
                if (version != Version) throw FileSystemException.Corrupted("unknown format version $version")

                SealedHeader(
                    segmentSize = buffer.int,
                    nonceSize = buffer.get().toInt() and 0xFF,
                    tagSize = buffer.get().toInt() and 0xFF,
                    cipherId = buffer.string(),
                    keyId = buffer.string(),
                    salt = ByteArray(SaltSize).also(buffer::get),
                )
            } catch (e: BufferUnderflowException) {
                throw FileSystemException.Corrupted("header truncated", e)
            } catch (e: IllegalArgumentException) {
                throw FileSystemException.Corrupted("header malformed", e)
            }
        }

        private fun ByteBuffer.string(): String {
            val bytes = ByteArray(get().toInt() and 0xFF).also(::get)
            return String(bytes, Charsets.UTF_8)
        }
    }
}
