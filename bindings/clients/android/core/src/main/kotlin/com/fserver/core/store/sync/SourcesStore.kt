package com.fserver.core.store.sync

import com.fserver.core.files.SourceLocation
import com.fserver.core.store.FServerStorageApi
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SourceTombstone
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.index.LocalIndexedFile
import kotlin.time.Instant

/**
 * The registered sources, as the engine needs them: enumerate them to work through, stamp what a
 * pass found.
 *
 * Disjoint from `RegisteredSourcesRepository` on purpose - the engine registers and marks off, the
 * UI lists and renames. See [com.fserver.core.store.FServerStorageApi].
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface SourcesStore {
    /** Every registered source. Read on each periodic pass, so it must be cheap. */
    suspend fun all(): List<SourceEntry>

    suspend fun findById(id: String): SourceEntry?

    suspend fun findByModeAndLocation(mode: SyncMode, location: SourceLocation): SourceEntry?

    /** Inserts, or replaces the record carrying the same [SourceEntry.id]. */
    suspend fun upsert(source: SourceEntry)

    /** Stamps [id] as having finished a clean pass [at], leaving the rest of the record alone. */
    suspend fun markSynced(id: String, at: Instant)

    /** Moves [id] to [status], leaving the rest of the record alone. */
    suspend fun updateStatus(id: String, status: SourceEntry.Status)

    /** Replaces [id]'s preferences, leaving the rest of the record alone. */
    suspend fun updatePreferences(id: String, preferences: SourceEntry.Preferences)

    /**
     * Drops the source and every [LocalIndexedFile] recorded against it, leaving a [SourceTombstone]
     * behind.
     *
     * The tombstone is not optional bookkeeping: it is the only thing that later tells the peer to
     * stop asking for the id, so an implementation that drops the source silently strands the
     * other half forever.
     */
    suspend fun delete(id: String)

    /** Leaves a location-less [SourceTombstone] for a request from [deviceId] this device refused. */
    suspend fun recordRefusal(id: String, deviceId: String)

    /** What [delete] or [recordRefusal] left behind, or null for an id this device never dropped. */
    suspend fun findTombstone(id: String): SourceTombstone?
}
