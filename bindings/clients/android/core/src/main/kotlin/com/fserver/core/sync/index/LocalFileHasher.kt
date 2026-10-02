package com.fserver.core.sync.index

import com.fserver.core.crypto.internal.SourceFileSystems
import com.fserver.core.files.util.FileHasher
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException
import kotlin.time.Instant

/**
 * Reads a local file whole and records its hash. Runs outside the index lock, so a long read does
 * not hold scans up; [LocalIndexWriter.recordHash] re-checks the row before writing.
 */
internal class LocalFileHasher(
    private val sourceFiles: SourceFileSystems,
    private val writer: LocalIndexWriter,
) {
    suspend fun hashFile(source: SourceEntry, local: FileRecord) {
        val locator = local.locator
            ?: error("Cannot hash file without locator: ${local.path} in source ${source.id}")

        hashFile(
            source = source,
            key = IndexedFileKey(fileId = local.id.value, sourceId = source.id),
            locator = locator,
            size = local.metadata.size,
            modifiedAt = local.metadata.lastModified,
        )
    }

    suspend fun hashFile(source: SourceEntry, local: LocalIndexedFile) {
        hashFile(
            source = source,
            key = IndexedFileKey(fileId = local.fileId, sourceId = source.id),
            locator = local.locator,
            size = local.size.bytes,
            modifiedAt = local.modifiedAt,
        )

        Timber.d("Hashed file ${local.path} in source ${source.id} with locator ${local.locator}")
    }

    private suspend fun hashFile(
        source: SourceEntry,
        key: IndexedFileKey,
        locator: String,
        size: Long,
        modifiedAt: Instant,
    ) {
        val hasher = FileHasher()

        withContext(Dispatchers.IO) {
            val fs = sourceFiles.open(source)
            val file = fs.openFile(locator) ?: throw FileNotFoundException(locator)

            file.read().use { stream ->
                val buffer = ByteArray(HashChunkSize)
                var bytesRead: Int

                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    hasher.write(buffer, bytesRead)
                }
            }
        }

        writer.recordHash(
            source = source,
            key = key,
            size = size,
            modifiedAt = modifiedAt,
            hash = hasher.compute(),
        )
    }

    private companion object {
        const val HashChunkSize = 8192
    }
}
