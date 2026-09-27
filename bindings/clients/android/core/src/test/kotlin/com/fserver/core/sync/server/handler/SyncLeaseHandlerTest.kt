package com.fserver.core.sync.server.handler

import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.lease.SyncLeaseRegistry
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.net.session.PeerSession
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Who is told what about a source they asked to hold.
 *
 * The refusals carry meaning - `Inactive` tells the peer to stop asking for good - so handing the
 * wrong one to the wrong device either strands a working pair or leaks that a source exists.
 */
class SyncLeaseHandlerTest {

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(localDeviceId = LocalId, clock = clock)
    private val registry = SyncLeaseRegistry(clock, SyncProgressReporter(clock))
    private val handler = SyncLeaseHandler(SourceAuthorizer(storage), storage, registry, mockk(relaxed = true), clock)

    @Before
    fun setUp() = runBlocking {
        storage.sources.upsert(sourceEntry(id = SourceId, deviceId = OwnerId))
    }

    @Test
    fun `the paired device is granted a free source`() = runTest {
        val replies = answer(OwnerId)

        assertTrue(replies.only<FileServerMessages.AcquireSyncLease.Granted>().sourceId == SourceId)
        assertNull(registry.beginAcquire(SourceId))
    }

    @Test
    fun `any other device is denied, and is not told the source exists`() = runTest {
        val replies = answer(StrangerId)

        val denial = replies.only<FileServerMessages.AcquireSyncLease.Denied>()
        // Denied, not Inactive: Inactive is a statement about a source this device actually has.
        assertTrue(denial.reason == "Source not found")
        assertNotNull(registry.beginAcquire(SourceId))
    }

    @Test
    fun `a source dropped here is answered Inactive, so the peer stops asking`() = runTest {
        storage.sources.updateStatus(SourceId, SourceEntry.Status.Disabled("removed by user"))

        val replies = answer(OwnerId)

        assertTrue(
            replies.only<FileServerMessages.AcquireSyncLease.Inactive>().reason == "removed by user"
        )
    }

    @Test
    fun `a local pass in flight denies the peer rather than refusing it for good`() = runTest {
        val local = checkNotNull(registry.beginAcquire(SourceId))
        registry.confirmLocal(SourceId, local)

        val replies = answer(OwnerId)

        assertTrue(replies.only<FileServerMessages.AcquireSyncLease.Denied>().sourceId == SourceId)
    }

    @Test
    fun `a device cannot release a lease another device holds`() = runTest {
        answer(OwnerId, leaseId = "owner-lease")

        handler.release(
            message = FileServerMessages.AcquireSyncLease.ReleaseLease(SourceId, "owner-lease"),
            peer = peerIdentity(StrangerId),
        )

        assertNull(registry.beginAcquire(SourceId))
    }

    @Test
    fun `a follower takes the initiator's mode and grants the lease`() = runTest {
        val replies = answer(OwnerId, syncMode = AskMode)

        replies.only<FileServerMessages.AcquireSyncLease.Granted>()
        assertEquals(AskMode, storage.sources.findById(SourceId)?.syncMode)
    }

    @Test
    fun `a follower mid-pass denies and keeps its mode until it grants`() = runTest {
        val local = checkNotNull(registry.beginAcquire(SourceId))
        registry.confirmLocal(SourceId, local)

        val replies = answer(OwnerId, syncMode = AskMode)

        replies.only<FileServerMessages.AcquireSyncLease.Denied>()
        assertEquals(sourceEntry().syncMode, storage.sources.findById(SourceId)?.syncMode)
    }

    @Test
    fun `an initiator sends its own mode back to a stale follower`() = runTest {
        storage.sources.upsert(
            sourceEntry(id = SourceId, deviceId = OwnerId, role = SourceEntry.Role.Initiator)
        )

        val replies = answer(OwnerId, syncMode = AskMode)

        val outdated = replies.only<FileServerMessages.AcquireSyncLease.Outdated>()
        assertEquals(sourceEntry().syncMode.toDto(), outdated.syncMode)
        assertNotNull(registry.beginAcquire(SourceId))
    }

    @Test
    fun `a different mode type is denied and changes nothing`() = runTest {
        val offload = SyncMode.Offload(SyncMode.Offload.EvictPolicy.OlderThanDays(30), keepPinned = true)

        val replies = answer(OwnerId, syncMode = offload)

        replies.only<FileServerMessages.AcquireSyncLease.Denied>()
        assertEquals(sourceEntry().syncMode, storage.sources.findById(SourceId)?.syncMode)
    }

    private suspend fun answer(
        deviceId: String,
        leaseId: String = "lease-1",
        syncMode: SyncMode = sourceEntry().syncMode,
    ): FakePeerSession.Replies {
        val replies = FakePeerSession.Replies()
        val request = FileServerMessages.AcquireSyncLease.Request(SourceId, leaseId, syncMode.toDto())

        handler.answer(
            event = PeerSession.Inbound(request, replies.channel),
            message = request,
            session = FakePeerSession(identity = peerIdentity(deviceId)),
        )

        return replies
    }

    private companion object {
        const val SourceId = "source-1"
        const val LocalId = "device-local"
        const val OwnerId = "device-owner"
        const val StrangerId = "device-stranger"
        val AskMode = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.Ask)
    }
}
