package com.fserver.core.journal.impl

import com.fserver.common.exception.FileSystemException
import com.fserver.common.exception.NetworkException
import com.fserver.common.exception.SyncException
import com.fserver.core.journal.JournalEvent
import com.fserver.core.journal.PassTally
import com.fserver.core.network.PeerIdentityMismatchException
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.progress.SyncFailure
import com.fserver.net.DictionaryMismatchException
import com.fserver.net.dictionary.MessageDictionary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class JournalWriterTest {
    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val writer = JournalWriter(storage, clock)
    private val source = sourceEntry(id = "s", deviceId = "d")

    @Test
    fun `a failed pass stays one open issue until a pass gets through`() = runTest {
        writer.passFailed(source, SyncException.RemoteRejectedException("no"))
        clock.advance(1.seconds)
        writer.passFailed(source, SyncException.RemoteRejectedException("no"))

        val open = storage.journal.all.single()
        assertEquals(JournalEvent.PassFailed("s", "d", SyncFailure.Reason.Refused), open.event)
        assertEquals(2, open.issue?.occurrences)

        writer.passSucceeded(source, PassTally(), conflicts = emptySet())

        assertTrue(storage.journal.all.single().issue!!.solved)
    }

    @Test
    fun `a pass with nothing to show is not recorded`() = runTest {
        writer.passSucceeded(source, PassTally(), conflicts = emptySet())
        writer.passSucceeded(source, PassTally(received = 2), conflicts = emptySet())

        assertEquals(
            listOf(JournalEvent.PassCompleted("s", "d", PassTally(received = 2))),
            storage.journal.all.map { it.event },
        )
    }

    @Test
    fun `sealed files that will not open are found among the failed actions`() = runTest {
        val failure = SyncException.ActionFailedException("pass failed")
            .apply { addSuppressed(RuntimeException("wrapped", FileSystemException.MissingKey("k"))) }

        writer.passFailed(source, failure)

        assertTrue(
            storage.journal.all.any {
                it.event == JournalEvent.SealedFilesUnreadable("s", JournalEvent.SealedFilesUnreadable.Problem.MissingKey)
            }
        )
    }

    @Test
    fun `a held conflict the pass no longer planned is solved, one still planned stays open`() = runTest {
        writer.raise(JournalEvent.ConflictHeld("s", "d", "gone", "a.txt"))
        writer.raise(JournalEvent.ConflictHeld("s", "d", "kept", "b.txt"))

        writer.passSucceeded(source, PassTally(), conflicts = setOf("kept"))

        val open = storage.journal.all.filter { it.issue?.solved == false }.map { (it.event as JournalEvent.ConflictHeld).fileId }
        assertEquals(listOf("kept"), open)
    }

    @Test
    fun `only a peer that answered and disagreed is an issue`() = runTest {
        writer.connectFailed("silent", NetworkException.NoRoute("nothing there"))
        writer.connectFailed("other", RuntimeException("wrapped", PeerIdentityMismatchException("other", "x")))
        writer.connectFailed(
            "old",
            DictionaryMismatchException(MessageDictionary.Descriptor(id = "FServer", version = 7), "too new"),
        )

        assertEquals(
            setOf(
                JournalEvent.ConnectionRefused("other", JournalEvent.ConnectionRefused.Reason.IdentityMismatch),
                JournalEvent.IncompatibleDictionary("old", localVersion = 1, remoteVersion = 7),
            ),
            storage.journal.all.map { it.event }.toSet(),
        )

        writer.connected("other")
        writer.connected("old")
        assertTrue(storage.journal.all.all { it.issue!!.solved })
    }

    @Test
    fun `a clock back in range solves the skew`() = runTest {
        writer.clockMeasured("d", offsetMs = 120_000, skewed = true)
        writer.clockMeasured("d", offsetMs = 10, skewed = false)

        assertTrue(storage.journal.all.single().issue!!.solved)
    }
}
