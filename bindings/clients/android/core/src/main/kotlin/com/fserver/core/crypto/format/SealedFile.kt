package com.fserver.core.crypto.format

import com.fserver.common.exception.FileSystemException
import com.fserver.core.crypto.spi.StorageCipher
import com.fserver.files.fs.FsReader
import com.fserver.files.fs.FsWriter
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.SecretKey

/**
 * One file's header bound to the cipher and key it names. Seals and opens single segments; the
 * reader and writer built from it lay them out.
 *
 * Every seal draws a fresh random nonce, so a segment rewritten in place never reuses one.
 */
internal class SealedFile(
    val header: SealedHeader,
    private val cipher: StorageCipher,
    private val key: SecretKey,
    private val random: SecureRandom,
) {
    val layout: SealedLayout get() = header.layout

    init {
        require(cipher.id == header.cipherId) { "Header names ${header.cipherId}, not ${cipher.id}" }
        if (cipher.nonceSize != header.nonceSize || cipher.tagSize != header.tagSize) {
            throw FileSystemException.Corrupted("header sizes disagree with ${cipher.id}")
        }
    }

    fun reader(raw: FsReader): FsReader = SealedReader(raw, this)

    /** [raw] is read back to rewrite partly covered segments; both are closed with the writer. */
    fun writer(raw: FsReader, out: FsWriter): FsWriter = SealedWriter(raw, out, this)

    /** Writes the header and one empty segment to a new, empty file. */
    suspend fun initialize(out: FsWriter) {
        out.write(0, header.encoded)
        out.write(layout.segmentOffset(0), seal(0, ByteArray(0), 0, last = true))
    }

    /** Nonce followed by the sealed [length] bytes of [plain]. */
    fun seal(index: Long, plain: ByteArray, length: Int, last: Boolean): ByteArray {
        val nonce = ByteArray(cipher.nonceSize).also(random::nextBytes)
        val sealed = cipher.seal(key, nonce, aad(index, last), plain.copyOf(length))
        check(sealed.size == length + cipher.tagSize) { "${cipher.id} sealed to an unexpected size" }
        return nonce + sealed
    }

    fun open(index: Long, raw: ByteArray, last: Boolean): ByteArray {
        val nonce = raw.copyOfRange(0, cipher.nonceSize)
        val sealed = raw.copyOfRange(cipher.nonceSize, raw.size)
        val plain = try {
            cipher.open(key, nonce, aad(index, last), sealed)
        } catch (e: GeneralSecurityException) {
            throw FileSystemException.Corrupted("segment $index failed authentication", e)
        }
        if (plain.size != raw.size - layout.overhead) throw FileSystemException.Corrupted("segment $index has a wrong size")
        return plain
    }

    private fun aad(index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(header.encoded.size + 9)
            .put(header.encoded)
            .putLong(index)
            .put(if (last) 1 else 0)
            .array()
}

/** Reads all [length] bytes at [offset], or fails: a short segment is a damaged file. */
internal suspend fun FsReader.readFully(offset: Long, length: Int): ByteArray {
    val bytes = ByteArray(length)
    // FsReader fills from index 0, so only a short first read needs a scratch buffer.
    var done = read(offset, bytes, length).coerceAtLeast(0)
    while (done < length) {
        val chunk = ByteArray(length - done)
        val read = read(offset + done, chunk)
        if (read <= 0) throw FileSystemException.Corrupted("ends at ${offset + done}, expected ${offset + length}")
        chunk.copyInto(bytes, done, 0, read)
        done += read
    }
    return bytes
}
