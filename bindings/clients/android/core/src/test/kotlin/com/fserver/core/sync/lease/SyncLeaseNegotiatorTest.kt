package com.fserver.core.sync.lease

import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.remote.PeerIndexFetcher
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The asking half of the lease. The answering half is `SyncLeaseHandler`, and the registry both
 * sides share is covered by `SyncLeaseRegistryTest`.
 *
 * `PeerIndexFetcher` is mocked rather than faked: it is a final class that dials through
 * `NetworkController`, and the only thing this class wants from it is a session.
 */
class SyncLeaseNegotiatorTest {

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(localDeviceId = LocalId, clock = clock)
    private val registry = SyncLeaseRegistry(clock, SyncProgressReporter(clock))
    private val peers = mockk<PeerIndexFetcher>()

    private val source = sourceEntry(id = SourceId, deviceId = PeerId)
    private val negotiator = SyncLeaseNegotiator(storage, registry, peers)

    @Before
    fun setUp() = runBlocking {
        storage.sources.upsert(source)
    }

    @Test
    fun `a granted lease runs the pass and is handed back afterwards`() = runTest {
        val session = session { granted(it) }
        var ran = false

        negotiator.runWithLease(source) { ran = true }

        assertTrue(ran)
        assertTrue(session.sent.any { it is FileServerMessages.AcquireSyncLease.ReleaseLease })
        // Released on both ends: the registry must not still think a pass is running here.
        assertNotNull(registry.beginAcquire(SourceId))
    }

    @Test
    fun `a denied lease skips the source without running anything`() = runTest {
        session {
            FileServerMessages.AcquireSyncLease.Denied(SourceId, "syncing there")
        }
        var ran = false

        negotiator.runWithLease(source) { ran = true }

        assertFalse(ran)
        assertNotNull(registry.beginAcquire(SourceId))
        assertEquals(SourceEntry.Status.Active, storage.sources.findById(SourceId)?.status)
    }

    @Test
    fun `a peer that dropped its half disables the source here`() = runTest {
        session {
            FileServerMessages.AcquireSyncLease.Inactive(SourceId, "removed on the other device")
        }
        var ran = false

        negotiator.runWithLease(source) { ran = true }

        assertFalse(ran)
        assertEquals(
            SourceEntry.Status.Disabled("removed on the other device"),
            storage.sources.findById(SourceId)?.status,
        )
    }

    @Test
    fun `a lease is handed back even when the pass throws`() = runTest {
        val session = session { granted(it) }

        val failure = runCatching {
            negotiator.runWithLease(source) { error("pass blew up") }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(session.sent.any { it is FileServerMessages.AcquireSyncLease.ReleaseLease })
        assertNotNull(registry.beginAcquire(SourceId))
    }

    @Test
    fun `a grant that arrives after the peer took the source over is handed straight back`() =
        runTest {
            // The peer's own request crossed ours on the wire and it wins the tie, so by the time
            // its grant lands the source is already being synced from the other side.
            val session = session { request ->
                runBlocking {
                    registry.grantToPeer(
                        sourceId = SourceId,
                        peerDeviceId = PeerId,
                        leaseId = "peer-lease",
                        localDeviceId = LocalId,
                    )
                }
                granted(request)
            }
            var ran = false

            negotiator.runWithLease(source) { ran = true }

            assertFalse(ran)
            assertTrue(session.sent.any { it is FileServerMessages.AcquireSyncLease.ReleaseLease })
        }

    @Test
    fun `an answer that is not part of the lease exchange fails the pass`() = runTest {
        session { FileServerMessages.FetchFiles.Failed(SourceId, "nonsense") }

        val failure = runCatching { negotiator.runWithLease(source) { } }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertNotNull(registry.beginAcquire(SourceId))
    }

    @Test
    fun `a link that cannot be opened leaves no lease behind`() = runTest {
        coEvery { peers.connectToDevice(any<SourceEntry>()) } throws IllegalStateException("offline")

        val failure = runCatching { negotiator.runWithLease(source) { } }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        // The claim was taken before dialing, so a failed dial has to give it back.
        assertNotNull(registry.beginAcquire(SourceId))
    }

    @Test
    fun `a source a local pass already holds is skipped rather than queued`() = runTest {
        val held = checkNotNull(registry.beginAcquire(SourceId))
        registry.confirmLocal(SourceId, held)
        var ran = false

        negotiator.runWithLease(source) { ran = true }

        assertFalse(ran)
    }

    private fun granted(request: FileServerMessages): FileServerMessages =
        FileServerMessages.AcquireSyncLease.Granted(
            sourceId = SourceId,
            leaseId = (request as FileServerMessages.AcquireSyncLease.Request).leaseId,
        )

    private fun session(
        responder: (FileServerMessages) -> FileServerMessages,
    ): FakePeerSession {
        val session = FakePeerSession(identity = peerIdentity(PeerId), responder = responder)
        coEvery { peers.connectToDevice(any<SourceEntry>()) } returns session
        return session
    }

    private companion object {
        const val SourceId = "source-1"
        const val LocalId = "device-local"
        const val PeerId = "device-earlier"
    }
}
