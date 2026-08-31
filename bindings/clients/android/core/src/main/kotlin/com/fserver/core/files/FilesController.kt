package com.fserver.core.files

import com.fserver.core.files.impl.toCore
import com.fserver.core.files.scan.DirectoryScanProgress
import com.fserver.core.files.scan.ScanSource
import com.fserver.core.files.scan.ScannedFile
import com.fserver.core.files.source.AccessModel
import com.fserver.core.files.source.FileSource
import com.fserver.core.files.sync.SourceSyncWorker
import com.fserver.core.store.FileSourcesStore
import com.fserver.core.utils.runBackgroundJob
import com.fserver.files.FilesNode
import java.util.UUID
import kotlin.time.Clock
import com.fserver.files.model.FileSystemException as FilesFileSystemException

/**
 * Owns the [FilesNode] and the set of registered sources.
 *
 * Registering and scanning live here rather than on a repository because both are engine actions:
 * the engine assigns the id, walks the tree, and stamps what a pass found. Listing what is
 * registered is a UI concern and lives on `FileSourcesRepository` in `:core:storage`.
 */
class FilesController internal constructor(
    private val node: FilesNode,
    private val store: FileSourcesStore,
    private val syncWorker: SourceSyncWorker,
) {
    suspend fun loadContent(
        directory: ScanSource,
        onProgress: (DirectoryScanProgress) -> Unit = {},
    ): Result<List<ScannedFile>> = runCatching {
        try {
            TODO()
            /*node
                .scanner
                .scan(directory = directory.toFiles())
                .map { it.toCore() }*/
        } catch (e: FilesFileSystemException) {
            throw e.toCore()
        }
    }

    /**
     * Registers a new [directory] + [accessModel] pair and persists it.
     *
     * The returned record carries the engine-assigned id; scan totals stay zero until a pass has
     * run over it.
     */
    suspend fun addSource(
        directory: ScanSource,
        accessModel: AccessModel,
        label: String,
    ): Result<FileSource> = runBackgroundJob {
        val source = FileSource(
            id = UUID.randomUUID().toString(),
            source = directory,
            accessModel = accessModel,
            label = label,
            createdAt = Clock.System.now(),
        )

        store.upsert(source)
        source
    }

    /** Changes what an already-registered source is allowed to do. */
    suspend fun updateAccessModel(id: String, accessModel: AccessModel): Result<Unit> =
        runBackgroundJob {
            val existing = store.findById(id)
                ?: throw IllegalArgumentException("No source registered with id: $id")

            store.upsert(existing.copy(accessModel = accessModel))
        }

    /**
     * Drops [id] from the registry. Nothing on disk is touched - unregistering is not eviction and
     * never a user delete.
     */
    suspend fun removeSource(id: String): Result<Unit> = runBackgroundJob {
        store.delete(id)
    }

    /**
     * Forgets what [id] has already worked through, so the next pass treats every file as new.
     * Bookkeeping only - no file on disk is touched.
     */
    suspend fun resetProgress(id: String): Result<Unit> = runBackgroundJob {
        store.clearProcessed(id)
    }

    /** Runs one pass now instead of waiting for the timer. */
    suspend fun syncNow(): Result<Unit> = runBackgroundJob {
        syncWorker.runOnce()
    }

    /** Starts the periodic pass. Idempotent - see [SourceSyncWorker] for what it can promise. */
    fun startPeriodicSync() {
        syncWorker.start()
    }

    suspend fun stopPeriodicSync() {
        syncWorker.stop()
    }
}
