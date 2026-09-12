package com.fserver.core.sync.server

import com.fserver.core.support.FakeStorage
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole access control on the answering side: every id a peer names is resolved here first.
 *
 * Two properties matter beyond "the right peer gets its source": a peer must not learn whether a
 * source it does not own exists, and it must not be able to move a source's status.
 */
class SourceAuthorizerTest {

    private val storage = FakeStorage()
    private val authorizer = SourceAuthorizer(storage)

    private val owner = peerIdentity(OwnerId)
    private val stranger = peerIdentity(StrangerId)

    @Test
    fun `an active source resolves for the device it syncs with`() = runTest {
        storage.sources.upsert(sourceEntry(id = SourceId, deviceId = OwnerId))

        val resolved = authorizer.resolve(owner, SourceId)

        assertTrue(resolved is ResolvedIncomingSource.Servable)
    }

    @Test
    fun `a source that syncs with another device is unknown, not gone`() = runTest {
        storage.sources.upsert(sourceEntry(id = SourceId, deviceId = OwnerId))

        val resolved = authorizer.resolve(stranger, SourceId)

        // Unknown and Gone are different answers: Gone would confirm the id exists here.
        assertEquals(ResolvedIncomingSource.Unknown, resolved)
    }

    @Test
    fun `an id nobody registered is unknown`() = runTest {
        assertEquals(ResolvedIncomingSource.Unknown, authorizer.resolve(owner, "never-registered"))
    }

    @Test
    fun `a disabled source is gone, with the reason, for its own device`() = runTest {
        storage.sources.upsert(
            sourceEntry(
                id = SourceId,
                deviceId = OwnerId,
                status = SourceEntry.Status.Disabled("user turned it off"),
            )
        )

        val resolved = authorizer.resolve(owner, SourceId)

        assertEquals(ResolvedIncomingSource.Gone("user turned it off"), resolved)
    }

    @Test
    fun `a disabled source is unknown to anyone else`() = runTest {
        storage.sources.upsert(
            sourceEntry(
                id = SourceId,
                deviceId = OwnerId,
                status = SourceEntry.Status.Disabled("user turned it off"),
            )
        )

        assertEquals(ResolvedIncomingSource.Unknown, authorizer.resolve(stranger, SourceId))
    }

    @Test
    fun `a pending source is activated by the device that is asking for it`() = runTest {
        storage.sources.upsert(
            sourceEntry(id = SourceId, deviceId = OwnerId, status = SourceEntry.Status.Pending)
        )

        val resolved = authorizer.resolve(owner, SourceId)

        assertEquals(
            SourceEntry.Status.Active,
            (resolved as ResolvedIncomingSource.Servable).source.status,
        )
        assertEquals(SourceEntry.Status.Active, storage.sources.findById(SourceId)?.status)
    }

    @Test
    fun `a stranger cannot activate a pending source`() = runTest {
        storage.sources.upsert(
            sourceEntry(id = SourceId, deviceId = OwnerId, status = SourceEntry.Status.Pending)
        )

        val resolved = authorizer.resolve(stranger, SourceId)

        assertEquals(ResolvedIncomingSource.Unknown, resolved)
        assertEquals(SourceEntry.Status.Pending, storage.sources.findById(SourceId)?.status)
    }

    @Test
    fun `a removed source is gone for the device it synced with`() = runTest {
        storage.sources.putTombstone(sourceId = SourceId, deviceId = OwnerId)

        assertTrue(authorizer.resolve(owner, SourceId) is ResolvedIncomingSource.Gone)
    }

    @Test
    fun `a removed source is unknown to anyone else`() = runTest {
        storage.sources.putTombstone(sourceId = SourceId, deviceId = OwnerId)

        assertEquals(ResolvedIncomingSource.Unknown, authorizer.resolve(stranger, SourceId))
    }

    @Test
    fun `authorizedSource refuses a source that is not this peer's`() = runTest {
        storage.sources.upsert(sourceEntry(id = SourceId, deviceId = OwnerId))

        val failure = runCatching { authorizer.authorizedSource(stranger, SourceId) }
            .exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `authorizedSource refuses a disabled source`() = runTest {
        storage.sources.upsert(
            sourceEntry(
                id = SourceId,
                deviceId = OwnerId,
                status = SourceEntry.Status.Disabled("dropped"),
            )
        )

        val failure = runCatching { authorizer.authorizedSource(owner, SourceId) }
            .exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    private companion object {
        const val SourceId = "source-1"
        const val OwnerId = "device-owner"
        const val StrangerId = "device-stranger"
    }
}
