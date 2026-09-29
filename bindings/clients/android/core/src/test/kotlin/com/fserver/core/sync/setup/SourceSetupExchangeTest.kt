package com.fserver.core.sync.setup

import com.fserver.core.sync.metadata.PeerSourceMetadata
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.SyncModeDto
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.peerMetadataExchange
import com.fserver.core.support.sourceEntry
import com.fserver.core.support.sourceMetadataDto
import com.fserver.core.sync.limits.SourceUsage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.remote.PeerConnector
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.hours

/**
 * Pairing a source across two devices: what a peer may ask for, and who may answer for it.
 *
 * A source only works while both sides hold a record under one id, so every message here either
 * creates that pairing or ends it - and each one names an id the *peer* chose.
 */
class SourceSetupExchangeTest {

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val peers = mockk<PeerConnector>()

    private val ownerSession = FakePeerSession(identity = peerIdentity(OwnerId))
    private val exchange = SourceSetupExchange(storage, peers, clock, peerMetadataExchange(storage, clock))

    @Before
    fun setUp() {
        coEvery { peers.connectToDevice(any<String>()) } returns ownerSession
    }

    @Test
    fun `an ask nobody has seen before is parked for the user`() = runTest {
        exchange.onRequest(peerIdentity(OwnerId), request())

        val parked = storage.sourceRequests.findById(SourceId)
        assertEquals(OwnerId, parked?.deviceId)
        assertEquals("Peer's photos", parked?.label)
        // Parked, not answered: the answer needs this device's user.
        assertTrue(ownerSession.sent.isEmpty())
    }

    @Test
    fun `a re-ask for a parked source keeps its place in the queue`() = runTest {
        exchange.onRequest(peerIdentity(OwnerId), request())
        val first = storage.sourceRequests.findById(SourceId)?.receivedAt

        clock.advance(1.hours)
        exchange.onRequest(peerIdentity(OwnerId), request())

        assertEquals(first, storage.sourceRequests.findById(SourceId)?.receivedAt)
    }

    @Test
    fun `a device cannot ask to host a source that syncs with another device`() = runTest {
        storage.sources.upsert(sourceEntry(id = SourceId, deviceId = OwnerId))

        exchange.onRequest(peerIdentity(StrangerId), request())

        assertNull(storage.sourceRequests.findById(SourceId))
        assertEquals(OwnerId, storage.sources.findById(SourceId)?.deviceId)
        assertTrue(ownerSession.sent.isEmpty())
    }

    @Test
    fun `a re-ask for something already accepted is answered again, not shown to the user twice`() =
        runTest {
            storage.sources.upsert(sourceEntry(id = SourceId, deviceId = OwnerId))

            exchange.onRequest(peerIdentity(OwnerId), request())

            assertNull(storage.sourceRequests.findById(SourceId))
            assertTrue(decision().accepted)
        }

    @Test
    fun `a re-ask for a source disabled here is refused with the reason`() = runTest {
        storage.sources.upsert(
            sourceEntry(
                id = SourceId,
                deviceId = OwnerId,
                status = SourceEntry.Status.Disabled("turned off here"),
            )
        )

        exchange.onRequest(peerIdentity(OwnerId), request())

        assertFalse(decision().accepted)
        assertEquals("turned off here", decision().reason)
    }

    @Test
    fun `a re-ask for a source removed here is refused rather than parked`() = runTest {
        storage.sources.putTombstone(sourceId = SourceId, deviceId = OwnerId)

        exchange.onRequest(peerIdentity(OwnerId), request())

        // Parking it again would ask the user to undo a decision they already made.
        assertNull(storage.sourceRequests.findById(SourceId))
        assertFalse(decision().accepted)
    }

    @Test
    fun `a tombstone left by another device does not answer for this peer`() = runTest {
        storage.sources.putTombstone(sourceId = SourceId, deviceId = StrangerId)

        exchange.onRequest(peerIdentity(OwnerId), request())

        assertEquals(OwnerId, storage.sourceRequests.findById(SourceId)?.deviceId)
    }

    @Test
    fun `accepting registers our half under the peer's id, as a follower`() = runTest {
        exchange.onRequest(peerIdentity(OwnerId), request())

        val source = exchange.accept(SourceId, SourceLocation.Internal(bucket = SourceId))

        assertEquals(SourceId, source.id)
        assertEquals(OwnerId, source.deviceId)
        assertEquals(SourceEntry.Role.Follower, source.role)
        assertEquals(SourceEntry.Status.Active, source.status)
        assertEquals(source, storage.sources.findById(SourceId))
        assertNull(storage.sourceRequests.findById(SourceId))
        assertTrue(decision().accepted)
    }

    @Test
    fun `accepting keeps this side's own preferences`() = runTest {
        exchange.onRequest(peerIdentity(OwnerId), request())
        val preferences = SourceEntry.Preferences.Default.copy(
            deviceConstraints = SourceEntry.Preferences.DeviceConstraints(
                wifiRequired = true,
                chargingRequired = true,
            ),
        )

        val source = exchange.accept(SourceId, SourceLocation.Internal(bucket = SourceId), preferences)

        assertEquals(preferences, source.preferences)
    }

    @Test
    fun `rejecting drops the ask and tells the peer, so it stops retrying`() = runTest {
        exchange.onRequest(peerIdentity(OwnerId), request())

        exchange.reject(SourceId)

        assertNull(storage.sourceRequests.findById(SourceId))
        assertNull(storage.sources.findById(SourceId))
        assertFalse(decision().accepted)
    }

    @Test
    fun `a re-ask after rejecting is refused again, not parked`() = runTest {
        exchange.onRequest(peerIdentity(OwnerId), request())
        exchange.reject(SourceId)
        ownerSession.sent.clear()

        exchange.onRequest(peerIdentity(OwnerId), request())

        assertNull(storage.sourceRequests.findById(SourceId))
        assertFalse(decision().accepted)
        assertEquals("Rejected by user", decision().reason)
    }

    @Test
    fun `accepting an ask that was never made is refused`() = runTest {
        val failure = runCatching {
            exchange.accept(SourceId, SourceLocation.Internal(bucket = SourceId))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `only the device a source syncs with can decide anything about it`() = runTest {
        storage.sources.upsert(
            sourceEntry(id = SourceId, deviceId = OwnerId, status = SourceEntry.Status.Pending)
        )

        exchange.onDecision(
            peer = peerIdentity(StrangerId),
            message = FileServerMessages.ConfigureSource.Decision(SourceId, accepted = false),
        )

        assertEquals(SourceEntry.Status.Pending, storage.sources.findById(SourceId)?.status)
    }

    @Test
    fun `the peer accepting moves our half off pending`() = runTest {
        storage.sources.upsert(
            sourceEntry(id = SourceId, deviceId = OwnerId, status = SourceEntry.Status.Pending)
        )

        exchange.onDecision(
            peer = peerIdentity(OwnerId),
            message = FileServerMessages.ConfigureSource.Decision(SourceId, accepted = true),
        )

        assertEquals(SourceEntry.Status.Active, storage.sources.findById(SourceId)?.status)
    }

    @Test
    fun `the peer refusing disables our half rather than dropping it`() = runTest {
        storage.sources.upsert(
            sourceEntry(id = SourceId, deviceId = OwnerId, status = SourceEntry.Status.Pending)
        )

        exchange.onDecision(
            peer = peerIdentity(OwnerId),
            message = FileServerMessages.ConfigureSource.Decision(
                sourceId = SourceId,
                accepted = false,
                reason = "no room",
            ),
        )

        // The record is what stops the next pass from asking again, and what the user reads.
        assertEquals(
            SourceEntry.Status.Disabled("no room"),
            storage.sources.findById(SourceId)?.status,
        )
    }

    @Test
    fun `an answer that cannot be delivered still leaves our half accepted`() = runTest {
        coEvery { peers.connectToDevice(any<String>()) } throws IllegalStateException("offline")
        exchange.onRequest(peerIdentity(OwnerId), request())

        val source = exchange.accept(SourceId, SourceLocation.Internal(bucket = SourceId))

        // Best effort on purpose: the peer re-asks, and the user's accept must not look failed.
        assertEquals(SourceEntry.Status.Active, source.status)
        assertEquals(source, storage.sources.findById(SourceId))
    }

    @Test
    fun `an accepted source keeps what the asker reported about its half`() = runTest {
        exchange.onRequest(peerIdentity(OwnerId), request())

        exchange.accept(SourceId, SourceLocation.Internal(bucket = SourceId))

        val asker = storage.sources.metadataOf(SourceId, OwnerId)
        assertEquals("/DCIM/Camera", asker?.storagePath)
        assertEquals(SourceUsage(files = 3, bytes = 300), asker?.usage)
        assertEquals(
            PeerSourceMetadata.StorageKind.AppStorage,
            storage.sources.metadataOf(SourceId, storage.identity.localDevice().deviceId)?.storageKind,
        )
    }

    @Test
    fun `a one-way follower does not keep the asker's report, which would never update`() = runTest {
        exchange.onRequest(peerIdentity(OwnerId), request(syncMode = SyncModeDto.Host))

        exchange.accept(SourceId, SourceLocation.Internal(bucket = SourceId))

        assertNull(storage.sources.metadataOf(SourceId, OwnerId))
    }

    private fun request(syncMode: SyncModeDto = SyncModeDto.Mirror()) = FileServerMessages.ConfigureSource.Request(
        sourceId = SourceId,
        label = "Peer's photos",
        syncMode = syncMode,
        metadata = sourceMetadataDto(),
    )

    private fun decision(): FileServerMessages.ConfigureSource.Decision =
        ownerSession.sent.filterIsInstance<FileServerMessages.ConfigureSource.Decision>().last()

    private companion object {
        const val SourceId = "source-chosen-by-peer"
        const val OwnerId = "device-owner"
        const val StrangerId = "device-stranger"
    }
}
