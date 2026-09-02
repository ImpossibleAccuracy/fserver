package com.fserver.core.sync

import com.fserver.common.exception.SyncException
import com.fserver.common.utils.runBackgroundJob
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.SourceLocation
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.runner.SyncRunner
import com.fserver.core.sync.setup.IncomingSourceRequest
import com.fserver.core.sync.setup.SourceSetupExchange
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.util.UUID

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
    private val sourceSetup: SourceSetupExchange,
    private val timeProvider: TimeProvider,
) {
    /** The newest source a peer has asked this device to host, or null when nothing is waiting. */
    val incomingRequest: Flow<IncomingSourceRequest?> get() = sourceSetup.pending

    /** Replaces the settings every source runs under. Takes effect on the next pass. */
    suspend fun updatePreferences(preferences: SyncPreferences) {
        storage.preferences.saveSourceRules(preferences)
    }

    /** Run a single sync pass over all registered sources. */
    suspend fun runSync() = syncRunner.runOnce()

    /**
     * Registers a new [location] + [syncMode] pair, persists it, and asks [deviceId] to register
     * the other half - a source neither side can sync until both hold a record under the same id.
     */
    suspend fun addSource(
        location: SourceLocation.Selectable,
        syncMode: SyncMode,
        deviceId: String,
        label: String,
    ): Result<SourceEntry> = runBackgroundJob {
        storage.sources.findByModeAndLocation(
            mode = syncMode,
            location = location
        )?.let {
            throw SyncException.DuplicateSourceException(
                sourceId = it.id,
                location = location.toString(),
                mode = syncMode.toString()
            )
        }

        val source = SourceEntry(
            id = UUID.randomUUID().toString(),
            deviceId = deviceId,
            location = location,
            syncMode = syncMode,
            // Asking is what makes this side the initiator, and one-way modes travel from here.
            role = SourceEntry.Role.Initiator,
            status = SourceEntry.Status.Pending,
            label = label,
            createdAt = timeProvider.now(),
        )

        storage.sources.upsert(source)

        // Best effort: peer may be off network right now, and the source is registered either way
        runCatchingCancellable { sourceSetup.requestRemote(source) }
            .exceptionOrNull()
            ?.let { Timber.w(it, "Could not ask $deviceId to host source ${source.id}") }

        source
    }

    /**
     * Takes on the source [sourceId] names, storing what arrives in [location].
     *
     * Registers this device's half under the id the peer chose, so both sides address the source
     * by the same one, and drops the request. The record lands [SourceEntry.Status.Active] as
     * [SourceEntry.Role.Follower] - accepting is this side's whole half of the setup.
     *
     * [location] defaults to app-private storage, which needs no grant and is scoped to this
     * source alone. A host that wants the files somewhere the user can reach passes a
     * [SourceLocation.Tree] or [SourceLocation.Directory] instead - one directory per source, and
     * whether the app may write there is the host's to have arranged.
     */
    suspend fun acceptRequest(
        sourceId: String,
        location: SourceLocation.Hostable = SourceLocation.Internal(bucket = sourceId),
    ): Result<SourceEntry> = runBackgroundJob {
        sourceSetup.accept(sourceId, location)
    }.onSuccess {
        syncRunner.runOnceAsync()
    }

    /** Refuses [sourceId] and tells the peer, so it drops its own half instead of retrying. */
    suspend fun rejectRequest(sourceId: String): Result<Unit> = runBackgroundJob {
        sourceSetup.reject(sourceId)
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

            storage.sources.findByModeAndLocation(mode = syncMode, location = existing.location)
                ?.takeIf { it.id != id }
                ?.let {
                    throw SyncException.DuplicateSourceException(
                        sourceId = it.id,
                        location = existing.location.toString(),
                        mode = syncMode.toString()
                    )
                }

            storage.sources.upsert(existing.copy(syncMode = syncMode))

            // TODO: notify peer about changes
        }.onSuccess {
            syncRunner.runOnceAsync()
        }

    /**
     * Drops [id] from the registry, leaving a tombstone so the peer is told the source is gone the
     * next time it asks, rather than retrying it forever.
     *
     * Nothing on disk is touched - unregistering is not eviction and never a user delete.
     */
    suspend fun removeSource(id: String): Result<Unit> = runBackgroundJob {
        storage.sources.delete(id)

        // TODO: notify peer about removal
    }.onSuccess {
        syncRunner.runOnceAsync()
    }
}
