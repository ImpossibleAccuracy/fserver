package com.fserver.core.crypto.internal

import com.fserver.common.exception.FileSystemException
import com.fserver.core.crypto.format.SealedFile
import com.fserver.core.crypto.format.SealedHeader
import com.fserver.core.crypto.format.SealedLayout
import com.fserver.core.crypto.format.readFully
import com.fserver.core.crypto.impl.AesGcmCipher
import com.fserver.core.crypto.model.AtRest
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

    /** Every cipher id registered here, the built-in one first. */
    val cipherIds: List<String> get() = byId.keys.toList()

    /** Whether [cipherId] can seal and open here. */
    fun supports(cipherId: String): Boolean = cipherId in byId

    suspend fun create(sourceId: String, cipherId: String): SealedFile {
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

    /** The header [raw] starts with, or null when it does not look sealed at all. */
    suspend fun headerOf(raw: FsReader): SealedHeader? {
        val head = raw.readFully(0, raw.size().coerceAtMost(SealedHeader.MaxSize.toLong()).toInt())
        return if (SealedHeader.hasMagic(head)) SealedHeader.decode(head) else null
    }

    /**
     * What a file of [plainSize] sealed as [atRest] measures on disk, assuming the default segment
     * size - a scan compares it with the length it found before it reads a header. Null for a
     * cipher this device does not have.
     */
    fun rawSizeOf(plainSize: Long, atRest: AtRest.Sealed): Long? {
        val cipher = byId[atRest.cipherId] ?: return null
        val layout = SealedLayout(
            headerSize = SealedHeader.sizeOf(atRest.cipherId, atRest.keyId),
            segmentSize = SealedHeader.DefaultSegmentSize,
            overhead = cipher.nonceSize + cipher.tagSize,
        )
        return layout.rawSize(plainSize)
    }

    private fun cipher(id: String): StorageCipher =
        byId[id] ?: throw FileSystemException.UnknownCipher(id)
}
