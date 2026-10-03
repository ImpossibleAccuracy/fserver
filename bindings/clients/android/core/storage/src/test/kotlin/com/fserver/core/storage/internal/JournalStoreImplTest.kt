package com.fserver.core.storage.internal

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.fserver.core.journal.JournalEvent
import com.fserver.core.journal.PassTally
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.progress.SyncFailure
import com.fserver.core.sync.progress.SyncFailureReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties
import kotlin.time.Instant

/** Against real SQLite: one open issue per key is the partial unique index's job, not Kotlin's. */
class JournalStoreImplTest {
    private val driver = JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties().apply { put("foreign_keys", "true") },
    ).also { FServerStorageDatabase.Schema.create(it) }

    private val database = FServerStorageDatabase(driver)
    private val store = JournalStoreImpl(database)

    @After
    fun tearDown() = driver.close()

    @Test
    fun `every kind survives a round trip`() = runTest {
        val events = listOf(
            JournalEvent.PassCompleted(
                "s", "d", "Photos", SyncMode.Type.Mirror,
                PassTally(sent = 1, received = 2, deletedHere = 3, deletedOnPeer = 7, moved = 4, evicted = 5, skipped = 6),
            ),
            JournalEvent.FileFetched("s", "d", "DCIM/IMG_2210.jpg", bytes = 4_800_000),
            JournalEvent.PeerPassFailed("s", "d", SyncFailureReason.TransferFailed),
            JournalEvent.ConflictResolved(
                "s", "d", "a/b.txt",
                JournalEvent.ConflictResolved.Outcome.KeptBoth,
                JournalEvent.ConflictResolved.DecidedBy.User,
            ),
            JournalEvent.ConflictDecisionDropped("s", "a/b.txt", JournalEvent.ConflictDecisionDropped.Reason.SideChanged),
            JournalEvent.DevicePaired("d", "Phone", AuthMethod.Pin),
            JournalEvent.DeviceForgotten("d", "Phone"),
            JournalEvent.SourceAdded("s", "d", "Photos", SyncMode.Type.Offload, SourceEntry.Role.Initiator),
            JournalEvent.SourceRemoved("s", "d", "Photos"),
            JournalEvent.SourceRequested("s", "d", "Photos", SyncMode.Type.Mirror),
            JournalEvent.SourceRequestRejected("s", "d", "Photos"),
            JournalEvent.SourceAcceptedByPeer("s", "d", "Photos"),
            JournalEvent.SourceDisabledByPeer("s", "d", "Photos", JournalEvent.SourceDisabledByPeer.Reason.Removed),
            JournalEvent.SourceChanged(
                "s", "d", "Photos",
                setOf(JournalEvent.SourceChanged.Change.Encryption, JournalEvent.SourceChanged.Change.FileLimits),
                JournalEvent.SourceChanged.ChangedBy.User,
            ),
            JournalEvent.SourceChanged("s", "d", "Photos", emptySet(), JournalEvent.SourceChanged.ChangedBy.Peer),
            JournalEvent.EncryptionMigrated("s", JournalEvent.EncryptionTarget.Encrypted, files = 12),
            JournalEvent.OneShotFinished(
                "t", "d", "Phone",
                JournalEvent.OneShotFinished.Direction.Incoming,
                JournalEvent.OneShotFinished.Outcome.Completed,
                files = 3,
            ),
        )
        val issues = listOf(
            JournalEvent.PassFailed("s", "d", SyncFailure.Reason.Unreachable),
            JournalEvent.SealedFilesUnreadable("s", JournalEvent.SealedFilesUnreadable.Problem.MissingKey),
            JournalEvent.ConflictHeld("s", "d", "f", "a/b.txt"),
            JournalEvent.ClockSkewed("d", offsetMs = -120_000),
            JournalEvent.IncompatibleDictionary("d", localVersion = 1, remoteVersion = 2),
            JournalEvent.ConnectionRefused("d", JournalEvent.ConnectionRefused.Reason.IdentityMismatch),
            JournalEvent.EncryptionIncomplete("s", JournalEvent.EncryptionTarget.Decrypted, failed = 2),
        )

        events.forEachIndexed { i, event -> store.append(event, at(i)) }
        issues.forEachIndexed { i, issue -> store.raise(issue, at(100 + i)) }

        val read = store.entries.first()
        assertEquals((events + issues).toSet(), read.map { it.event }.toSet())
        assertTrue(read.filter { it.event is JournalEvent.Issue }.all { it.issue?.solved == false })
        assertTrue(read.filterNot { it.event is JournalEvent.Issue }.all { it.issue == null })
    }

    @Test
    fun `a repeat folds into the open issue and carries the newest payload`() = runTest {
        store.raise(JournalEvent.ClockSkewed("d", 60_000), at(1))
        store.raise(JournalEvent.ClockSkewed("d", 90_000), at(2))

        val entry = store.entries.first().single()
        assertEquals(JournalEvent.ClockSkewed("d", 90_000), entry.event)
        assertEquals(at(1), entry.at)
        assertEquals(at(2), entry.issue?.lastSeenAt)
        assertEquals(2, entry.issue?.occurrences)
    }

    @Test
    fun `a repeat after the issue was solved opens a new one`() = runTest {
        store.raise(JournalEvent.ClockSkewed("d", 60_000), at(1))
        assertTrue(store.solve(JournalEvent.ClockSkewed("d", 0).key, at(2)))
        assertFalse(store.solve(JournalEvent.ClockSkewed("d", 0).key, at(3)))

        store.raise(JournalEvent.ClockSkewed("d", 70_000), at(4))

        val (newest, oldest) = store.entries.first()
        assertNull(newest.issue?.solvedAt)
        assertEquals(at(2), oldest.issue?.solvedAt)
    }

    @Test
    fun `solving by id ignores entries that are not open issues`() = runTest {
        store.append(JournalEvent.SourceRemoved("s", "d", "Photos"), at(1))
        store.raise(JournalEvent.PassFailed("s", "d", SyncFailure.Reason.Refused), at(2))
        val (issue, event) = store.entries.first()

        assertFalse(store.solve(event.id, at(3)))
        assertTrue(store.solve(issue.id, at(3)))
        assertFalse(store.solve(issue.id, at(4)))
    }

    @Test
    fun `issues are solved by the source or device they are about`() = runTest {
        store.raise(JournalEvent.PassFailed("s", "d", SyncFailure.Reason.Refused), at(1))
        store.raise(JournalEvent.ConflictHeld("s", "d", "f", "a.txt"), at(2))
        store.raise(JournalEvent.ClockSkewed("other", 60_000), at(3))

        store.solveForSource("s", at(4))
        assertEquals(listOf("other"), store.entries.first().filter { it.issue?.solved == false }.map { it.event.deviceId })
        assertTrue(store.openIssues("s").isEmpty())

        store.solveForDevice("other", at(5))
        assertTrue(store.entries.first().all { it.issue?.solved == true })
    }

    @Test
    fun `trimming and clearing keep open issues whatever their age`() = runTest {
        store.raise(JournalEvent.ClockSkewed("d", 60_000), at(0))
        repeat(5) { store.append(JournalEvent.SourceRemoved("s$it", "d", "x"), at(1 + it)) }

        store.trim(keep = 2)
        val trimmed = store.entries.first()
        assertEquals(3, trimmed.size)
        assertTrue(trimmed.any { it.event is JournalEvent.ClockSkewed })

        store.clear()
        assertEquals(listOf(JournalEvent.ClockSkewed("d", 60_000)), store.entries.first().map { it.event })
        // The attributes of dropped entries go with them.
        assertEquals(1, database.attributeQueries.selectAllOf(JournalRecords.Owner).executeAsList().size)
    }

    private fun at(seconds: Int) = Instant.fromEpochSeconds(1_000L + seconds)
}
