package com.fserver.core.sync.index

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.IdGenerator
import com.fserver.common.utils.SourcePaths
import com.fserver.core.files.scan.toFiles
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.scan.FoundFile
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

        val actualState = node.scanner
            .scan(source.location.toFiles())
            .result().getOrThrow()

        val new = mutableListOf<FoundFile>()
        val updated = mutableMapOf<IndexedFile, FoundFile>()

        for (file in actualState) {
            val saved = savedByPath.remove(file.path)
            if (saved == null) {
                new += file
            } else if (file.lastModified != saved.modifiedAt || file.size != saved.size) {
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

        store.index.markProcessed(
            indexed = toSave,
            deleted = toDelete.map { it.id },
        )

        return toSave
    }

    companion object {
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
    state = state,
    size = size,
    modifiedAt = lastModified,
    hash = hash,
    revision = revision,
    processedAt = currentTime,
)
