package com.fserver.core.sync

import com.fserver.common.exception.SyncException
import com.fserver.common.utils.runBackgroundJob
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.ensureSourceReachable
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.progress.SyncProgressRepository
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.runner.SyncRunner
import com.fserver.core.sync.setup.IncomingSourceRequest
import com.fserver.core.sync.setup.SourceSetupExchange
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private val requirementsChecker: RequirementsChecker,
    private val sessionProgressReporter: SyncProgressReporter,
    private val localIndexer: LocalChangesIndexer,
    private val backgroundScope: BackgroundScope,
) {
    /** Initial scan-then-ask per new source, cancelled if the source is removed before it ends. */
    private val introductions = ConcurrentHashMap<String, Job>()

    /** Every source a peer has asked this device to host, oldest first. */
    val incomingRequests: Flow<List<IncomingSourceRequest>> get() = sourceSetup.pending

    /** Currently running operations, and their progress. */
    val progress: SyncProgressRepository = sessionProgressReporter

    /**
     * Modes a source at [location] may be registered under, safest first.
     *
     * Mirror and Host write the peer's files into [location], so they need one that can be written
     * to as a directory - the media library is not.
     */
    fun availableModes(location: SourceLocation.Selectable): List<SyncMode.Type> = buildList {
        if (location is SourceLocation.Hostable) add(SyncMode.Type.Mirror)
        add(SyncMode.Type.AutoUpload)
        add(SyncMode.Type.Offload)
        if (location is SourceLocation.Hostable) add(SyncMode.Type.Host)
    }

    /** Run a single sync pass over all registered sources. */
    suspend fun runSync() = syncRunner.runOnce()

    /**
     * Run a single sync pass over [sourceId] only. [force] ignores the device constraints (Wi-Fi,
     * charging), for a pass the user started by hand.
     */
    suspend fun runSync(sourceId: String, force: Boolean = false) =
        syncRunner.runSource(sourceId, force)

    /**
     * Brings the index of [sourceId] in line with its folder now, without syncing. Followed through
     * [SyncProgressRepository.indexing].
     */
    suspend fun index(sourceId: String): Result<Unit> = runBackgroundJob {
        val source = storage.sources.findById(sourceId)
            ?: throw IllegalArgumentException("Source $sourceId is not registered")

        localIndexer.refresh(source)
    }

    /**
     * Registers a new [location] + [syncMode] pair, persists it, and asks [deviceId] to register
     * the other half - a source neither side can sync until both hold a record under the same id.
     *
     * Returns once registered. The ask carries the source's size, so it goes out after an initial
     * scan in the background, followed through [SyncProgressRepository.indexing].
     */
    suspend fun addSource(
        location: SourceLocation.Selectable,
        syncMode: SyncMode,
        deviceId: String,
        label: String,
        preferences: SourceEntry.Preferences = SourceEntry.Preferences.Default,
    ): Result<SourceEntry> = runBackgroundJob {
        requirementsChecker.ensureSourceReachable(location)

        require(syncMode.type in availableModes(location)) {
            "${syncMode.type} is not available for $location"
        }

        ensureNoDuplicate(syncMode, location)

        val source = SourceEntry(
            id = UUID.randomUUID().toString(),
            deviceId = deviceId,
            location = location,
            syncMode = syncMode,
            preferences = preferences,
            // Asking is what makes this side the initiator, and one-way modes travel from here.
            role = SourceEntry.Role.Initiator,
            status = SourceEntry.Status.Pending,
            label = label,
            createdAt = timeProvider.now(),
        )

        storage.sources.upsert(source)

        Timber.i("Registered new source ${source.id} at $location for $deviceId, asking it to host")
        introduce(source)

        source
    }

    private fun introduce(source: SourceEntry) {
        val job = backgroundScope.launch(start = CoroutineStart.LAZY) {
            // A failed scan still asks.
            runCatchingCancellable { localIndexer.refresh(source) }
                .exceptionOrNull()
                ?.let { Timber.w(it, "Could not index source ${source.id} before asking ${source.deviceId}") }

            // Best effort: peer may be off network right now, and the source is registered either way
            runCatchingCancellable { sourceSetup.requestRemote(source) }
                .exceptionOrNull()
                ?.let { Timber.w(it, "Could not ask ${source.deviceId} to host source ${source.id}") }
        }

        introductions[source.id] = job
        job.invokeOnCompletion { introductions.remove(source.id, job) }
        job.start()
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
     *
     * [preferences] are this device's own: the mode comes from the peer, when and how much to
     * take in does not.
     */
    suspend fun acceptRequest(
        sourceId: String,
        location: SourceLocation.Hostable = SourceLocation.Internal(bucket = sourceId),
        preferences: SourceEntry.Preferences = SourceEntry.Preferences.Default,
    ): Result<SourceEntry> = runBackgroundJob {
        requirementsChecker.ensureSourceReachable(location)

        Timber.i("Accepting source $sourceId at $location")
        sourceSetup.accept(sourceId, location, preferences)
    }.onSuccess {
        syncRunner.runOnceAsync()
    }

    /** Refuses [sourceId] and tells the peer, so it drops its own half instead of retrying. */
    suspend fun rejectRequest(sourceId: String): Result<Unit> = runBackgroundJob {
        Timber.i("Rejecting source $sourceId")
        sourceSetup.reject(sourceId)
    }

    /**
     * Changes [id]'s [preferences] and the settings of the mode it already runs under.
     *
     * Switching to a different mode type is refused: flipping it under a live index would
     * re-interpret records written under the old rules. The mode belongs to the initiator, so a
     * follower may change its preferences only; the peer picks up a new mode at the next lease.
     */
    suspend fun updateSource(
        id: String,
        syncMode: SyncMode,
        preferences: SourceEntry.Preferences,
    ): Result<SourceEntry> = runBackgroundJob {
        val existing = storage.sources.findById(id)
            ?: throw IllegalArgumentException("No source registered with id: $id")

        require(existing.syncMode.type == syncMode.type) {
            "Cannot change sync mode type from ${existing.syncMode.type} to ${syncMode.type}"
        }

        require(existing.role == SourceEntry.Role.Initiator || existing.syncMode == syncMode) {
            "Only the initiator may change the mode of source $id"
        }

        ensureNoDuplicate(syncMode, existing.location, except = id)

        val updated = existing.copy(syncMode = syncMode, preferences = preferences)
        storage.sources.upsert(updated)

        Timber.i("Updated source $id: $syncMode, $preferences")
        updated
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
        // A scan still running would write index rows back after the delete, and ask for a gone source.
        introductions[id]?.cancelAndJoin()
        storage.sources.delete(id)

        // TODO: notify peer about removal
    }.onSuccess {
        syncRunner.runOnceAsync()
    }

    /** One source per mode and location: two would sync the same files twice. */
    private suspend fun ensureNoDuplicate(mode: SyncMode, location: SourceLocation, except: String? = null) {
        storage.sources.findByModeAndLocation(mode = mode, location = location)
            ?.takeIf { it.id != except }
            ?.let {
                throw SyncException.DuplicateSourceException(
                    sourceId = it.id,
                    location = location.toString(),
                    mode = mode.toString()
                )
            }
    }
}
