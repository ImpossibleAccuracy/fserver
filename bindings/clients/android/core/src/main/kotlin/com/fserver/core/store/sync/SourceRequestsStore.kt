package com.fserver.core.store.sync

import com.fserver.core.store.FServerStorageApi
import com.fserver.core.sync.setup.IncomingSourceRequest
import kotlinx.coroutines.flow.Flow

/**
 * Sources a peer has asked this device to host, until its user answers.
 *
 * Persisted rather than held in memory: the ask arrives whenever the peer's user registers the
 * source, and must survive until someone opens the app - see [IncomingSourceRequest].
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface SourceRequestsStore {
    /** Everything still waiting on an answer, oldest first. */
    fun pending(): Flow<List<IncomingSourceRequest>>

    suspend fun findById(sourceId: String): IncomingSourceRequest?

    /** Inserts, or replaces the request carrying the same source id. */
    suspend fun upsert(request: IncomingSourceRequest)

    /** Drops the request, answered either way. */
    suspend fun delete(sourceId: String)
}
