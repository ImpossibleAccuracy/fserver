package com.fserver.core.sync

import android.content.Context
import com.fserver.common.exception.SyncException
import com.fserver.common.utils.runBackgroundJob
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.StorageVolumes
import com.fserver.core.files.ensureSourceReachable
import com.fserver.core.files.toOriginPath
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.model.SyncPreferences
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.progress.SyncProgressRepository
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
    private val context: Context,
    private val storage: FServerStorage,
    private val syncRunner: SyncRunner,
    private val sourceSetup: SourceSetupExchange,
    private val timeProvider: TimeProvider,
    private val requirementsChecker: RequirementsChecker,
    private val sessionProgressReporter: SyncProgressReporter,
) {
    /** Every source a peer has asked this device to host, oldest first. */
    val incomingRequests: Flow<List<IncomingSourceRequest>> get() = sourceSetup.pending

    /** Currently running operations, and their progress. */
    val progress: SyncProgressRepository = sessionProgressReporter

    /**
     * Modes a source at [location] may be registered under, safest first.
     *
     * Mirror writes the peer's changes back into [location], so it needs one that can be written
     * to as a directory - the media library is not.
     */
    fun availableModes(location: SourceLocation.Selectable): List<SyncMode.Type> = buildList {
        if (location is SourceLocation.Hostable) add(SyncMode.Type.Mirror)
        add(SyncMode.Type.AutoUpload)
        add(SyncMode.Type.Offload)
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
        requirementsChecker.ensureSourceReachable(location)

        require(syncMode.type in availableModes(location)) {
            "${syncMode.type} is not available for $location"
        }

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
            originPath = location.toOriginPath(StorageVolumes.fromContext(context).volumes),
            syncMode = syncMode,
            // Asking is what makes this side the initiator, and one-way modes travel from here.
            role = SourceEntry.Role.Initiator,
            status = SourceEntry.Status.Pending,
            label = label,
            createdAt = timeProvider.now(),
        )

        storage.sources.upsert(source)

        Timber.i("Registered new source ${source.id} at $location for $deviceId, asking it to host")

        // Best effort: peer may be off network right now, and the source is registered either way
        runCatchingCancellable { sourceSetup.requestRemote(source) }
            .exceptionOrNull()
            ?.let { Timber.w(it, "Could not ask $deviceId to host source ${source.id}") }

        source
    }

    /** Replaces the settings every source runs under. Takes effect on the next pass. */
    suspend fun updatePreferences(preferences: SyncPreferences) {
        storage.preferences.saveSourceRules(preferences)
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
        requirementsChecker.ensureSourceReachable(location)

        Timber.i("Accepting source $sourceId at $location")
        sourceSetup.accept(sourceId, location)
    }.onSuccess {
        syncRunner.runOnceAsync()
    }

    /** Refuses [sourceId] and tells the peer, so it drops its own half instead of retrying. */
    suspend fun rejectRequest(sourceId: String): Result<Unit> = runBackgroundJob {
        Timber.i("Rejecting source $sourceId")
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

            require(existing.syncMode.type == syncMode.type) {
                "Cannot change sync mode type from ${existing.syncMode.type} to ${syncMode.type}"
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
