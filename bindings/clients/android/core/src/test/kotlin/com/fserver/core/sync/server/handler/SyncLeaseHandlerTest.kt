package com.fserver.core.sync.server.handler

import com.fserver.core.journal.impl.JournalWriter
import com.fserver.core.sync.metadata.PeerSourceMetadata
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.SourceMetadataDto
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.peerMetadataExchange
import com.fserver.core.support.sourceEntry
import com.fserver.core.support.sourceMetadataDto
import com.fserver.core.sync.lease.SyncLeaseRegistry
import com.fserver.core.sync.lease.SyncModeReconciler
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.progress.impl.SyncProgressReporter
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
    private val handler = SyncLeaseHandler(SourceAuthorizer(storage), storage, registry, SyncModeReconciler(storage), mockk(relaxed = true), peerMetadataExchange(storage, clock), JournalWriter(storage, clock))

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
        val offload = SyncMode.Offload(SyncMode.Offload.EvictPolicy.OlderThanDays(30))

        val replies = answer(OwnerId, syncMode = offload)

        replies.only<FileServerMessages.AcquireSyncLease.Denied>()
        assertEquals(sourceEntry().syncMode, storage.sources.findById(SourceId)?.syncMode)
    }

    @Test
    fun `an initiator of a one-way source never leases it to its follower`() = runTest {
        val autoUpload = SyncMode.AutoUpload(ignoreFilesBefore = null)
        storage.sources.upsert(
            sourceEntry(id = SourceId, deviceId = OwnerId, syncMode = autoUpload, role = SourceEntry.Role.Initiator)
        )

        val replies = answer(OwnerId, syncMode = autoUpload)

        replies.only<FileServerMessages.AcquireSyncLease.Denied>()
        assertNotNull(registry.beginAcquire(SourceId))
    }

    @Test
    fun `a follower of a one-way source leases it to its initiator`() = runTest {
        storage.sources.upsert(sourceEntry(id = SourceId, deviceId = OwnerId, syncMode = SyncMode.Host))

        answer(OwnerId, syncMode = SyncMode.Host).only<FileServerMessages.AcquireSyncLease.Granted>()
    }

    @Test
    fun `a mirror grant records the requester's half and reports ours back`() = runTest {
        val reported = sourceMetadataDto()

        val granted = answer(OwnerId, metadata = reported).only<FileServerMessages.AcquireSyncLease.Granted>()

        assertEquals(SourceMetadataDto.StorageKind.AppStorage, granted.metadata?.storageKind)
        assertEquals(reported.storagePath, storage.sources.metadataOf(SourceId, OwnerId)?.storagePath)
        assertEquals(PeerSourceMetadata.StorageKind.AppStorage, storage.sources.metadataOf(SourceId, LocalId)?.storageKind)
    }

    @Test
    fun `a one-way follower reports its half with the grant`() = runTest {
        storage.sources.upsert(sourceEntry(id = SourceId, deviceId = OwnerId, syncMode = SyncMode.Host))

        val granted = answer(OwnerId, syncMode = SyncMode.Host).only<FileServerMessages.AcquireSyncLease.Granted>()

        assertNotNull(granted.metadata)
    }

    @Test
    fun `a one-way follower ignores metadata its initiator should not have sent`() = runTest {
        storage.sources.upsert(sourceEntry(id = SourceId, deviceId = OwnerId, syncMode = SyncMode.Host))

        answer(OwnerId, syncMode = SyncMode.Host, metadata = sourceMetadataDto())
            .only<FileServerMessages.AcquireSyncLease.Granted>()

        assertNull(storage.sources.metadataOf(SourceId, OwnerId))
    }

    @Test
    fun `a denied request records nothing`() = runTest {
        val local = checkNotNull(registry.beginAcquire(SourceId))
        registry.confirmLocal(SourceId, local)

        answer(OwnerId, metadata = sourceMetadataDto()).only<FileServerMessages.AcquireSyncLease.Denied>()

        assertNull(storage.sources.metadataOf(SourceId, OwnerId))
    }

    private suspend fun answer(
        deviceId: String,
        leaseId: String = "lease-1",
        syncMode: SyncMode = sourceEntry().syncMode,
        metadata: SourceMetadataDto? = null,
    ): FakePeerSession.Replies {
        val replies = FakePeerSession.Replies()
        val request = FileServerMessages.AcquireSyncLease.Request(SourceId, leaseId, syncMode.toDto(), metadata)

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
