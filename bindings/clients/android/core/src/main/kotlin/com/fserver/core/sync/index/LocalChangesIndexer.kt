package com.fserver.core.sync.index

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.IdGenerator
import com.fserver.common.utils.SourcePaths
import com.fserver.core.files.scan.toFiles
import com.fserver.core.files.util.FileHasher
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.fs.FoundFile
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

internal class LocalChangesIndexer(
    private val store: FServerStorage,
    private val node: FilesNode,
    private val timeProvider: TimeProvider,
) {
    suspend fun refresh(source: SourceEntry): List<IndexedFile> {
        val currentTime = timeProvider.now()
        val device = store.identity.localDevice()

        val savedState = store.index.processedFiles(source.id)
        val savedByPath = savedState
            .associateBy { it.path }
            .toMutableMap()

        val actualState = node.openSource(source.location.toFiles())
            .scan()
            .result().getOrThrow()

        val new = mutableListOf<FoundFile>()
        val updated = mutableMapOf<IndexedFile, FoundFile>()

        for (file in actualState) {
            val saved = savedByPath.remove(file.path)
            if (saved == null) {
                new += file
            } else if (file.lastModified != saved.modifiedAt || file.size != saved.size) {
                updated[saved] = file
            } else if (saved.state !is IndexedFile.State.Present) {
                // File was deleted but now is back
                updated[saved] = file
            }
        }

        val toSave = ArrayList<IndexedFile>(new.size + updated.size).apply {
            for (file in new) {
                this += file.toIndexed(
                    id = IdGenerator.nextId,
                    sourceId = source.id,
                    fileId = SourcePaths.fileId(file.path),
                    state = IndexedFile.State.Present(
                        pinned = false,
                    ),
                    hash = null,
                    revision = IndexedFile.Revision(
                        originDevice = device.deviceId,
                        counter = InitialRevisionCounter,
                    ),
                    currentTime = currentTime,
                )
            }

            for ((saved, file) in updated) {
                // This pass is the write, so the counter advances. Adopting a peer's file restarts
                // the count instead: counters are only ever compared within one originDevice.
                val counter = saved.revision
                    ?.takeIf { it.originDevice == device.deviceId }
                    ?.let { it.counter + 1 }
                    ?: InitialRevisionCounter

                this += file.toIndexed(
                    id = saved.id,
                    sourceId = source.id,
                    fileId = saved.fileId,
                    state =
                        // Restore file if it was deleted and now is back
                        saved.state as? IndexedFile.State.Present
                            ?: IndexedFile.State.Present(
                                pinned = false,
                            ),
                    hash = null,
                    revision = IndexedFile.Revision(
                        originDevice = device.deviceId,
                        counter = counter,
                    ),
                    currentTime = currentTime,
                )
            }
        }

        val toDelete = savedByPath.values.filter {
            it.state is IndexedFile.State.Present
        }

        store.index.markProcessed(toSave)

        store.index.updateStateBatch(
            keys = toDelete.map { IndexedFileKey(it.fileId, it.sourceId) },
            state = IndexedFile.State.Deleted(
                deletedAt = currentTime,
            )
        )

        // Return full state after all writes
        return store.index.processedFiles(source.id)
    }

    /** Run hash computation for file record */
    suspend fun hashFile(source: SourceEntry, local: FileRecord) {
        val locator = local.locator
            ?: error("Cannot hash file without locator: ${local.path} in source ${source.id}")

        val key = IndexedFileKey(local.id.value, source.id)
        hashFile(source, key, locator)
    }

    /** Run hash computation for indexed file */
    suspend fun hashFile(source: SourceEntry, local: IndexedFile) {
        val locator = local.locator

        val key = IndexedFileKey(local.fileId, source.id)
        hashFile(source, key, locator)
    }

    /**
     * Compute the hash of a file and store it in the index.
     * Keep private, so callers can't break anything.
     */
    private suspend fun hashFile(
        source: SourceEntry,
        key: IndexedFileKey,
        locator: String,
    ) {
        val hasher = FileHasher()

        withContext(Dispatchers.IO) {
            val fs = node.openSource(source.location.toFiles())
            fs.openFile(locator).use { stream ->
                val buffer = ByteArray(HashChunkSize)
                var bytesRead: Int

                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    hasher.write(buffer, bytesRead)
                }
            }
        }

        store.index.saveHash(
            key = key,
            hash = hasher.compute(),
        )
    }

    companion object {
        private const val HashChunkSize = 8192 // 8 KB chunk size

        private const val InitialRevisionCounter = 1L
    }
}

private fun FoundFile.toIndexed(
    id: String,
    sourceId: String,
    fileId: String,
    state: IndexedFile.State,
    hash: ContentHash?,
    revision: IndexedFile.Revision?,
    currentTime: Instant,
) = IndexedFile(
    id = id,
    sourceId = sourceId,
    fileId = fileId,
    path = path,
    locator = locator,
    state = state,
    size = size,
    modifiedAt = lastModified,
    hash = hash,
    revision = revision,
    processedAt = currentTime,
)
