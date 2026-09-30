package com.fserver.core.storage.internal

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.fserver.core.files.SourceLocation
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
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

/**
 * Against real SQLite: the rules that matter here - ids are never overwritten, finished is final -
 * live in the SQL, not in Kotlin.
 */
class OneShotTransfersStoreImplTest {
    private val driver = JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties().apply { put("foreign_keys", "true") },
    ).also { FServerStorageDatabase.Schema.create(it) }

    private val database = FServerStorageDatabase(driver)
    private val store = OneShotTransfersStoreImpl(database)

    @After
    fun tearDown() = driver.close()

    @Test
    fun `every variant survives a round trip`() = runTest {
        val destinations = listOf(
            SourceLocation.Tree("content://tree/primary%3ADownload"),
            SourceLocation.Directory("/storage/emulated/0/Download"),
            SourceLocation.Internal("inbox"),
            SourceLocation.Downloads("FServer"),
        )

        val transfers = listOf(
            transfer("out", direction = OneShotTransfer.Direction.Outgoing),
            transfer("in-pending", direction = OneShotTransfer.Direction.Incoming(null)),
            transfer(
                "failed",
                status = OneShotTransfer.Status.Failed("peer went away"),
                finishedAt = Instant.fromEpochMilliseconds(2_000),
            ),
        ) + destinations.mapIndexed { i, destination ->
            transfer(
                "in-$i",
                direction = OneShotTransfer.Direction.Incoming(destination),
                status = OneShotTransfer.Status.Active,
                files = listOf(
                    file(0, locator = "content://media/1", committed = 10, status = OneShotTransferFile.Status.Completed),
                    file(1, status = OneShotTransferFile.Status.Failed("hash mismatch")),
                    file(2),
                ),
            )
        }

        for (transfer in transfers) {
            assertTrue(store.insert(transfer))
            assertEquals(transfer, store.find(transfer.id))
        }
    }

    @Test
    fun `a taken id is never overwritten`() = runTest {
        val original = transfer("t", files = listOf(file(0), file(1)))
        store.insert(original)

        val intruder = transfer(
            "t",
            peer = OneShotTransfer.Peer("device-other", "Other"),
            files = listOf(file(0, name = "evil.apk")),
        )

        assertFalse(store.insert(intruder))
        assertEquals(original, store.find("t"))
    }

    @Test
    fun `finished is final`() = runTest {
        store.insert(transfer("t", direction = OneShotTransfer.Direction.Outgoing))
        val end = Instant.fromEpochMilliseconds(5_000)

        assertTrue(store.updateStatus("t", OneShotTransfer.Status.Cancelled, end))
        assertFalse(store.updateStatus("t", OneShotTransfer.Status.Active, end))
        store.updateFile("t", file(0, locator = "x", committed = 10, status = OneShotTransferFile.Status.Completed))
        store.checkpoint("t", 0, 5)

        val stored = store.find("t")!!
        assertEquals(OneShotTransfer.Status.Cancelled, stored.status)
        assertEquals(end, stored.finishedAt)
        assertEquals(file(0), stored.files.single())
    }

    @Test
    fun `only final statuses stamp an end`() = runTest {
        store.insert(transfer("t", direction = OneShotTransfer.Direction.Outgoing))

        store.updateStatus("t", OneShotTransfer.Status.Active, Instant.fromEpochMilliseconds(5_000))

        assertNull(store.find("t")!!.finishedAt)
    }

    @Test
    fun `accept takes only a pending incoming transfer`() = runTest {
        val destination = SourceLocation.Internal("inbox")
        store.insert(transfer("in"))
        store.insert(transfer("out", direction = OneShotTransfer.Direction.Outgoing))
        store.insert(transfer("cancelled"))
        store.updateStatus("cancelled", OneShotTransfer.Status.Cancelled, Instant.fromEpochMilliseconds(1))

        assertTrue(store.accept("in", destination))
        assertFalse(store.accept("in", SourceLocation.Internal("other")))
        assertFalse(store.accept("out", destination))
        assertFalse(store.accept("cancelled", destination))
        assertFalse(store.accept("missing", destination))

        val accepted = store.find("in")!!
        assertEquals(OneShotTransfer.Status.Active, accepted.status)
        assertEquals(OneShotTransfer.Direction.Incoming(destination), accepted.direction)
        assertEquals(OneShotTransfer.Direction.Incoming(null), store.find("cancelled")!!.direction)
    }

    @Test
    fun `file progress is recorded while unfinished`() = runTest {
        store.insert(transfer("t", files = listOf(file(0), file(1))))

        store.checkpoint("t", 1, 7)
        store.updateFile("t", file(0, locator = "content://media/9", committed = 10, status = OneShotTransferFile.Status.Completed))

        assertEquals(
            listOf(
                file(0, locator = "content://media/9", committed = 10, status = OneShotTransferFile.Status.Completed),
                file(1, committed = 7),
            ),
            store.find("t")!!.files,
        )
    }

    @Test
    fun `unfinished lists pending and active, oldest first`() = runTest {
        store.insert(transfer("new", createdAt = 3))
        store.insert(transfer("old", createdAt = 1))
        store.insert(transfer("done", createdAt = 2))
        store.updateStatus("done", OneShotTransfer.Status.Completed, Instant.fromEpochMilliseconds(4))

        assertEquals(listOf("old", "new"), store.unfinished().map { it.id })
    }

    @Test
    fun `history drops only finished transfers, with everything they own`() = runTest {
        store.insert(transfer("active"))
        store.accept("active", SourceLocation.Internal("inbox"))
        store.insert(transfer("done"))
        store.accept("done", SourceLocation.Internal("inbox"))
        store.updateStatus("done", OneShotTransfer.Status.Completed, Instant.fromEpochMilliseconds(4))

        assertFalse(store.delete("active"))
        assertTrue(store.delete("done"))
        assertEquals(listOf("active"), store.transfers.first().map { it.id })

        // Nothing of the old record leaks into a new one under the same id.
        assertTrue(store.insert(transfer("done", files = listOf(file(5)))))
        assertEquals(transfer("done", files = listOf(file(5))), store.find("done"))

        store.updateStatus("done", OneShotTransfer.Status.Declined, Instant.fromEpochMilliseconds(6))
        store.clearFinished()
        assertEquals(listOf("active"), store.transfers.first().map { it.id })
        assertTrue(database.attributeQueries.selectByOwner(SourceRecords.OwnerOneShotTransfer, "done").executeAsList().isEmpty())
    }

    @Test
    fun `a row that does not rebuild is skipped`() = runTest {
        store.insert(transfer("good", createdAt = 1))
        store.insert(transfer("bad", createdAt = 2))
        driver.execute(null, "UPDATE oneShotTransfer SET status = 'Failed' WHERE id = 'bad'", 0)

        assertEquals(listOf("good"), store.transfers.first().map { it.id })
        assertNull(store.find("bad"))
    }

    private fun transfer(
        id: String,
        peer: OneShotTransfer.Peer = OneShotTransfer.Peer("device-peer", "Pixel"),
        direction: OneShotTransfer.Direction = OneShotTransfer.Direction.Incoming(null),
        status: OneShotTransfer.Status = OneShotTransfer.Status.Pending,
        files: List<OneShotTransferFile> = listOf(file(0)),
        createdAt: Long = 1_000,
        finishedAt: Instant? = null,
    ) = OneShotTransfer(
        id = id,
        peer = peer,
        direction = direction,
        status = status,
        files = files,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        finishedAt = finishedAt,
    )

    private fun file(
        index: Int,
        name: String = "photo-$index.jpg",
        locator: String? = null,
        committed: Long = 0,
        status: OneShotTransferFile.Status = OneShotTransferFile.Status.Pending,
    ) = OneShotTransferFile(
        index = index,
        name = name,
        size = 10,
        locator = locator,
        committedBytes = committed,
        status = status,
    )
}
