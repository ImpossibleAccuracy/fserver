package com.fserver.core.storage.internal

import com.fserver.core.store.sync.SourceRequestsStore
import com.fserver.core.sync.setup.IncomingSourceRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TODO: in-memory scaffolding, and the one store where that is actually wrong - a request is meant
 *  to outlive the process it arrived in. Swap the `MutableStateFlow` for a SQLDelight
 *  `sourceRequest` table once `SyncMode` has the column adapter `SourcesStoreImpl` is waiting on.
 */
internal class SourceRequestsStoreImpl : SourceRequestsStore {
    private val writeLock = Mutex()

    /** Oldest first, so the newest is the one a screen surfaces. */
    private val state = MutableStateFlow<List<IncomingSourceRequest>>(emptyList())

    override fun pending(): Flow<List<IncomingSourceRequest>> = state.asStateFlow()

    override suspend fun findById(sourceId: String): IncomingSourceRequest? =
        state.value.find { it.sourceId == sourceId }

    override suspend fun upsert(request: IncomingSourceRequest) = writeLock.withLock {
        state.update { current ->
            val position = current.indexOfFirst { it.sourceId == request.sourceId }
            if (position < 0) current + request
            else current.toMutableList().apply { this[position] = request }
        }
    }

    override suspend fun delete(sourceId: String) = writeLock.withLock {
        state.update { current -> current.filterNot { it.sourceId == sourceId } }
    }
}
