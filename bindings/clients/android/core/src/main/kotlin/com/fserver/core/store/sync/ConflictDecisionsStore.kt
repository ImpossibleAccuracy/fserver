package com.fserver.core.store.sync

import com.fserver.core.store.FServerStorageApi
import com.fserver.core.sync.conflict.ConflictDecision
import com.fserver.core.sync.index.IndexedFileKey
import kotlinx.coroutines.flow.Flow

/** User decisions on held conflicts, until a pass carries them out. Dropped with their source. */
@SubclassOptInRequired(FServerStorageApi::class)
interface ConflictDecisionsStore {
    val all: Flow<List<ConflictDecision>>

    suspend fun find(key: IndexedFileKey): ConflictDecision?

    suspend fun forSource(sourceId: String): List<ConflictDecision>

    /** Replaces an earlier decision on the same file. */
    suspend fun put(decision: ConflictDecision)

    suspend fun remove(key: IndexedFileKey)
}
