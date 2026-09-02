package com.fserver.core.sync.lease

import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The half of the lease protocol that decides who runs, without a peer on the other end. */
class SyncLeaseRegistryTest {

    private val clock = FakeTimeProvider()
    private val registry = SyncLeaseRegistry(clock)

    @Test
    fun `a peer holding the source blocks a local pass`() = runBlocking {
        assertTrue(registry.grantToPeer(Source, PeerId, "peer-lease", LocalId))

        assertNull(registry.beginAcquire(Source))
    }

    @Test
    fun `a running local pass is not handed to a peer`() = runBlocking {
        val lease = checkNotNull(registry.beginAcquire(Source))
        assertTrue(registry.confirmLocal(Source, lease))

        assertFalse(registry.grantToPeer(Source, PeerId, "peer-lease", LocalId))
    }

    @Test
    fun `both asking at once - the lower device id wins`() = runBlocking {
        val lower = SyncLeaseRegistry(clock)
        val higher = SyncLeaseRegistry(clock)

        val lowerLease = checkNotNull(lower.beginAcquire(Source))
        val higherLease = checkNotNull(higher.beginAcquire(Source))

        // Each side answers the request that crossed its own on the wire.
        val higherGranted = lower.grantToPeer(Source, HigherId, higherLease, LowerId)
        val lowerGranted = higher.grantToPeer(Source, LowerId, lowerLease, HigherId)

        assertFalse(higherGranted)
        assertTrue(lowerGranted)

        assertTrue(lower.confirmLocal(Source, lowerLease))
        assertFalse(higher.confirmLocal(Source, higherLease))
    }

    @Test
    fun `an expired peer lease frees the source`() = runBlocking {
        assertTrue(registry.grantToPeer(Source, PeerId, "peer-lease", LocalId))

        clock.advance(31.minutes)

        assertNotNull(registry.beginAcquire(Source))
    }

    @Test
    fun `a peer re-asking for the lease it holds keeps it`() = runBlocking {
        assertTrue(registry.grantToPeer(Source, PeerId, "peer-lease", LocalId))

        assertTrue(registry.grantToPeer(Source, PeerId, "peer-lease-2", LocalId))
        assertFalse(registry.grantToPeer(Source, "other-device", "other-lease", LocalId))
    }

    @Test
    fun `releasing what a peer no longer holds leaves the current holder alone`() = runBlocking {
        assertTrue(registry.grantToPeer(Source, PeerId, "current", LocalId))

        registry.releaseFromPeer(Source, PeerId, "stale")

        assertNull(registry.beginAcquire(Source))
    }

    @Test
    fun `a dropped session frees everything that peer held`() = runBlocking {
        assertTrue(registry.grantToPeer(Source, PeerId, "peer-lease", LocalId))

        registry.releaseAllFrom(PeerId)

        assertNotNull(registry.beginAcquire(Source))
    }

    private class FakeTimeProvider : TimeProvider {
        private var now = Instant.fromEpochSeconds(0)

        override fun now(): Instant = now

        fun advance(by: Duration) {
            now += by
        }
    }

    private companion object {
        const val Source = "source-1"
        const val LocalId = "device-local"
        const val PeerId = "device-peer"

        const val LowerId = "device-a"
        const val HigherId = "device-b"
    }
}
