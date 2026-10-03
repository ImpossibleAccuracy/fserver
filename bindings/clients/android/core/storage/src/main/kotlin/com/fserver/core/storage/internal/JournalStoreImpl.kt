package com.fserver.core.storage.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.fserver.core.journal.IssueState
import com.fserver.core.journal.JournalEntry
import com.fserver.core.journal.JournalEvent
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.store.journal.JournalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import kotlin.time.Instant
import com.fserver.core.storage.database.Attribute as DBAttribute
import com.fserver.core.storage.database.JournalEntry as DBJournalEntry

/** `journalEntry` rows, with each event's fields in `attribute` rows through [JournalRecords]. */
internal class JournalStoreImpl(
    private val database: FServerStorageDatabase,
) : JournalStore {
    private val dao = database.journalEntryQueries
    private val attributeDao = database.attributeQueries

    override val entries: Flow<List<JournalEntry>> = combine(
        dao.selectAll().asFlow().mapToList(Dispatchers.IO),
        attributeDao.selectAllOf(JournalRecords.Owner).asFlow().mapToList(Dispatchers.IO),
        ::assembleEntries,
    )

    override suspend fun append(event: JournalEvent, at: Instant) = database.transaction {
        insert(event, at, issueKey = null)
    }

    override suspend fun raise(issue: JournalEvent.Issue, at: Instant) = database.transaction {
        val open = dao.selectOpenIssue(issue.key).executeAsOneOrNull()
        if (open == null) {
            insert(issue, at, issueKey = issue.key)
            return@transaction
        }

        dao.seenAgain(
            kind = JournalRecords.kindOf(issue),
            sourceId = issue.sourceId,
            deviceId = issue.deviceId,
            at = at.toEpochMilliseconds(),
            id = open.id,
        )
        writeEvent(open.id, issue)
    }

    override suspend fun solve(key: String, at: Instant): Boolean =
        dao.solveByKey(at = at.toEpochMilliseconds(), issueKey = key).value > 0

    override suspend fun solve(id: Long, at: Instant): Boolean =
        dao.solveById(at = at.toEpochMilliseconds(), id = id).value > 0

    override suspend fun solveForSource(sourceId: String, at: Instant) {
        dao.solveBySource(at = at.toEpochMilliseconds(), sourceId = sourceId)
    }

    override suspend fun solveForDevice(deviceId: String, at: Instant) {
        dao.solveByDevice(at = at.toEpochMilliseconds(), deviceId = deviceId)
    }

    override suspend fun openIssues(sourceId: String): List<JournalEntry> {
        val rows = dao.selectOpenIssuesOf(sourceId, ::DBJournalEntry).executeAsList()
        if (rows.isEmpty()) return emptyList()

        return assembleEntries(
            rows = rows,
            attributes = attributeDao.selectByOwnerIds(JournalRecords.Owner, rows.map { it.id.toString() })
                .executeAsList(),
        )
    }

    override suspend fun trim(keep: Int) {
        dao.trim(keep.toLong())
    }

    override suspend fun clear() {
        dao.deleteSettled()
    }

    private fun insert(event: JournalEvent, at: Instant, issueKey: String?) {
        val atMs = at.toEpochMilliseconds()

        dao.insert(
            kind = JournalRecords.kindOf(event),
            sourceId = event.sourceId,
            deviceId = event.deviceId,
            atEpochMs = atMs,
            issueKey = issueKey,
            lastSeenAtEpochMs = atMs.takeIf { issueKey != null },
            occurrences = 1L.takeIf { issueKey != null },
        )
        writeEvent(dao.lastInsertedId().executeAsOne(), event)
    }

    private fun writeEvent(id: Long, event: JournalEvent) {
        val ownerId = id.toString()
        attributeDao.deleteByOwnerId(owner = JournalRecords.Owner, ownerId = ownerId)

        for (attribute in JournalRecords.attributesOf(event)) {
            attributeDao.insert(
                owner = JournalRecords.Owner,
                ownerId = ownerId,
                type = attribute.type,
                fieldName = attribute.field,
                fieldValue = attribute.value,
            )
        }
    }
}

/** Rows that will not rebuild are skipped and logged, never defaulted - see [SourceRecords]. */
private fun assembleEntries(rows: List<DBJournalEntry>, attributes: List<DBAttribute>): List<JournalEntry> {
    val attributesByEntry = attributes.groupBy { it.ownerId }

    return rows.mapNotNull { row ->
        val reader = SourceRecords.Reader(
            attributesByEntry[row.id.toString()]
                ?.associate { (it.type to it.fieldName) to it.fieldValue }
                .orEmpty()
        )

        runCatching { entryOf(row, reader) }
            .onFailure { Timber.w(it, "Skipping journal entry ${row.id}") }
            .getOrNull()
    }
}

private fun entryOf(row: DBJournalEntry, reader: SourceRecords.Reader): JournalEntry {
    val event = JournalRecords.eventOf(row.kind, row.sourceId, row.deviceId, reader)
    check((event is JournalEvent.Issue) == (row.issueKey != null)) { "issue key does not match kind '${row.kind}'" }

    return JournalEntry(
        id = row.id,
        event = event,
        at = Instant.fromEpochMilliseconds(row.atEpochMs),
        issue = row.issueKey?.let {
            IssueState(
                lastSeenAt = Instant.fromEpochMilliseconds(requireNotNull(row.lastSeenAtEpochMs) { "issue without lastSeen" }),
                occurrences = requireNotNull(row.occurrences) { "issue without occurrences" }.toInt(),
                solvedAt = row.solvedAtEpochMs?.let(Instant::fromEpochMilliseconds),
            )
        },
    )
}
