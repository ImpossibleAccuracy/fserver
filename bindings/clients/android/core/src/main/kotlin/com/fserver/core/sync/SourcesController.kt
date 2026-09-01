package com.fserver.core.sync

import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.files.SourceLocation
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.runner.SyncRunner
import java.util.UUID
import kotlin.time.Clock

/**
 * The registry of synced sources, and the one way a host changes it.
 *
 * Registering lives here rather than on a repository because it is an engine action: the engine
 * assigns the id, decides what a pass does with the source, and kicks that pass off. Listing what
 * is registered is a UI concern and lives on `RegisteredSourcesRepository` in `:core:storage`.
 */
class SourcesController internal constructor(
    private val storage: FServerStorage,
    private val syncRunner: SyncRunner,
) {
    /** Replaces the settings every source runs under. Takes effect on the next pass. */
    suspend fun updatePreferences(preferences: SyncPreferences) {
        storage.preferences.saveSourceRules(preferences)
    }

    /** Run a single sync pass over all registered sources. */
    suspend fun runSync() = syncRunner.runOnce()

    /**
     * Registers a new [location] + [syncMode] pair and persists it.
     *
     * The returned record carries the engine-assigned id; scan totals stay zero until a pass has
     * run over it.
     */
    suspend fun addSource(
        location: SourceLocation,
        syncMode: SyncMode,
        deviceId: String,
        label: String,
    ): Result<SourceEntry> = runBackgroundJob {
        val source = SourceEntry(
            id = UUID.randomUUID().toString(),
            deviceId = deviceId,
            location = location,
            syncMode = syncMode,
            label = label,
            createdAt = Clock.System.now(),
        )

        storage.sources.upsert(source)
        source
    }.onSuccess {
        syncRunner.runOnceAsync()
    }

    /**
     * Changes the settings of the mode [id] already runs under. Switching to a different mode is
     * refused: what the engine may do with a source is fixed when it is registered, and flipping
     * it under a live index would re-interpret records written under the old rules.
     */
    suspend fun updateAccessModel(id: String, syncMode: SyncMode): Result<Unit> =
        runBackgroundJob {
            val existing = storage.sources.findById(id)
                ?: throw IllegalArgumentException("No source registered with id: $id")

            // Check syncMode has same type as existing, otherwise throw an error
            if (existing.syncMode::class != syncMode::class) {
                throw IllegalArgumentException(
                    "Cannot change sync mode type from ${existing.syncMode::class.simpleName} to ${syncMode::class.simpleName}"
                )
            }

            storage.sources.upsert(existing.copy(syncMode = syncMode))
        }.onSuccess {
            syncRunner.runOnceAsync()
        }

    /**
     * Drops [id] from the registry. Nothing on disk is touched - unregistering is not eviction and
     * never a user delete.
     */
    suspend fun removeSource(id: String): Result<Unit> = runBackgroundJob {
        storage.sources.delete(id)
    }.onSuccess {
        syncRunner.runOnceAsync()
    }
}
