package com.fserver.core.storage.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.store.sync.ConflictDecisionsStore
import com.fserver.core.sync.conflict.ConflictDecision
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.version.HlcTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import com.fserver.core.storage.database.ConflictDecision as DBConflictDecision

internal class ConflictDecisionsStoreImpl(
    database: FServerStorageDatabase,
) : ConflictDecisionsStore {
    private val dao = database.conflictDecisionQueries

    override val all: Flow<List<ConflictDecision>> = dao.selectAll()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { rows -> rows.mapNotNull { it.toDomainModel() } }

    override suspend fun find(key: IndexedFileKey): ConflictDecision? =
        dao.selectByKey(sourceId = key.sourceId, fileId = key.fileId)
            .executeAsOneOrNull()
            ?.toDomainModel()

    override suspend fun forSource(sourceId: String): List<ConflictDecision> =
        dao.selectBySource(sourceId).executeAsList().mapNotNull { it.toDomainModel() }

    override suspend fun put(decision: ConflictDecision) {
        dao.upsert(
            sourceId = decision.sourceId,
            fileId = decision.fileId,
            choice = decision.choice.name,
            localHlc = decision.local?.hlc?.packed,
            localOrigin = decision.local?.originDevice,
            remoteHlc = decision.remote?.hlc?.packed,
            remoteOrigin = decision.remote?.originDevice,
            decidedAtEpochMs = decision.decidedAt.toEpochMilliseconds(),
        )
    }

    override suspend fun remove(key: IndexedFileKey) {
        dao.deleteByKey(sourceId = key.sourceId, fileId = key.fileId)
    }
}

/** A choice this build does not know reads as no decision: the conflict is simply shown again. */
private fun DBConflictDecision.toDomainModel(): ConflictDecision? {
    val choice = ConflictDecision.Choice.entries.firstOrNull { it.name == choice } ?: return null

    return ConflictDecision(
        sourceId = sourceId,
        fileId = fileId,
        choice = choice,
        local = seen(localHlc, localOrigin),
        remote = seen(remoteHlc, remoteOrigin),
        decidedAt = Instant.fromEpochMilliseconds(decidedAtEpochMs),
    )
}

private fun seen(hlc: Long?, origin: String?): ConflictDecision.SeenVersion? =
    if (hlc == null || origin == null) null
    else ConflictDecision.SeenVersion(HlcTimestamp(hlc), origin)
