package com.fserver.core.files.util

import com.fserver.common.model.ContentHash
import java.security.MessageDigest

/**
 * Content hash built from chunks, so a file is never held whole in memory.
 * One instance hashes one file: [compute] finalizes the digest and ends its life.
 */
class FileHasher(val algorithm: String = HASH_ALGORITHM) {
    private val digest = MessageDigest.getInstance(algorithm)

    fun write(data: ByteArray, bytesRead: Int) {
        digest.update(data, 0, bytesRead)
    }

    fun write(data: ByteArray, offset: Int, length: Int) {
        digest.update(data, offset, length)
    }

    fun compute(): ContentHash {
        val hash = digest.digest().joinToString("") { "%02x".format(it) }

        return ContentHash(value = hash, algorithm = algorithm)
    }

    companion object {
        private const val HASH_ALGORITHM = "SHA-256"
    }
}
