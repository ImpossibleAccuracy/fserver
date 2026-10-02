package com.fserver.core.crypto.internal

import com.fserver.common.exception.FileSystemException
import com.fserver.core.crypto.format.SealedFile
import com.fserver.core.crypto.format.SealedHeader
import com.fserver.core.crypto.format.readFully
import com.fserver.core.crypto.impl.AesGcmCipher
import com.fserver.core.crypto.spi.StorageCipher
import com.fserver.core.store.crypto.StorageKeysStore
import com.fserver.files.fs.FsReader
import java.security.SecureRandom

/**
 * Binds sealed files to their cipher and key: a new one under the source's current key, an
 * existing one under whatever its header names. [ciphers] come on top of the built-in one.
 */
internal class SealedFiles(
    ciphers: List<StorageCipher>,
    private val keys: StorageKeysStore,
    private val random: SecureRandom = SecureRandom(),
) {
    private val byId: Map<String, StorageCipher> = (listOf(AesGcmCipher) + ciphers).let { all ->
        val duplicate = all.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicate.isEmpty()) { "Storage cipher ids registered twice: $duplicate" }
        all.associateBy { it.id }
    }

    suspend fun create(sourceId: String, cipherId: String = AesGcmCipher.id): SealedFile {
        val cipher = cipher(cipherId)
        val key = keys.current(sourceId)
        val header = SealedHeader(
            segmentSize = SealedHeader.DefaultSegmentSize,
            nonceSize = cipher.nonceSize,
            tagSize = cipher.tagSize,
            cipherId = cipher.id,
            keyId = key.id,
            salt = ByteArray(SealedHeader.SaltSize).also(random::nextBytes),
        )
        return SealedFile(header, cipher, key.secret, random)
    }

    /** The file [raw] holds, by its own header. Never falls back to reading it as plaintext. */
    suspend fun open(raw: FsReader): SealedFile {
        val head = raw.readFully(0, raw.size().coerceAtMost(SealedHeader.MaxSize.toLong()).toInt())
        val header = SealedHeader.decode(head)
        val key = keys.resolve(header.keyId) ?: throw FileSystemException.MissingKey(header.keyId)
        return SealedFile(header, cipher(header.cipherId), key, random)
    }

    private fun cipher(id: String): StorageCipher =
        byId[id] ?: throw FileSystemException.UnknownCipher(id)
}
