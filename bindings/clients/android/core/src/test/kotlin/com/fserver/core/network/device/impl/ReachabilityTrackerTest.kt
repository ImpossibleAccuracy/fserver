package com.fserver.core.network.device.impl

import com.fserver.common.exception.NetworkException
import com.fserver.common.exception.SyncException
import com.fserver.core.network.DeviceUnreachableException
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.network.TransportKind
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.DeviceMetadata
import com.fserver.core.network.device.model.FailedContact
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * What a failure is filed as, when it stops being reported, and - the part that needs the record to
 * outlive the process - when a run of them turns from "that device is off" into a fault.
 *
 * The classification is what the user is shown, so it is asserted per case rather than trusted to
 * an exception name: a transport that fails in its own words still has to land where the user can
 * act on it.
 */
class ReachabilityTrackerTest {

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val tracker = ReachabilityTracker(storage, clock)

    @Test
    fun `no route recorded means there was nothing to dial`() = runTest {
        trust()

        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))

        assertEquals(FailedContact.Reason.NoRoute, reasonOf(Peer))
    }

    @Test
    fun `a route that did not answer is unreachable, and names the transport`() = runTest {
        trust()

        tracker.recordFailure(
            Peer,
            DeviceUnreachableException(Peer, transport = TransportKind.MulticastDns),
        )

        val failure = tracker.device(Peer).first()

        assertEquals(FailedContact.Reason.Unreachable, failure?.reason)
        assertEquals(TransportKind.MulticastDns, failure?.transport)
    }

    @Test
    fun `a peer that answered and said no is refused`() = runTest {
        trust()

        tracker.recordFailure(Peer, SyncException.RemoteRejectedException("not yours"))

        assertEquals(FailedContact.Reason.Refused, reasonOf(Peer))
    }

    @Test
    fun `a missing permission is the OS, not the peer`() = runTest {
        trust()

        tracker.recordFailure(Peer, RequirementsNotMetException(RequirementReport.Satisfied))

        assertEquals(FailedContact.Reason.NotAllowed, reasonOf(Peer))
    }

    @Test
    fun `a wrapped cause is classified by what it wraps`() = runTest {
        trust()

        tracker.recordFailure(
            Peer,
            IllegalStateException("no session", NetworkException.Transport("connection refused")),
        )

        assertEquals(FailedContact.Reason.Unreachable, reasonOf(Peer))
    }

    @Test
    fun `nothing is kept for a device this side never trusted`() = runTest {
        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))

        assertNull(tracker.device(Peer).first())
    }

    @Test
    fun `one failed attempt reads as a device that is simply off`() = runTest {
        trust(lastSeen = 30.days.ago())

        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))

        assertFalse(tracker.device(Peer).first()!!.isWarning)
    }

    @Test
    fun `failing again after days out of contact is worth reporting`() = runTest {
        trust(lastSeen = 30.days.ago())

        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))
        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))

        val failure = tracker.device(Peer).first()!!

        assertTrue(failure.isWarning)
        assertEquals(2, failure.attempts)
    }

    @Test
    fun `a device seen this morning is not reported however often it fails`() = runTest {
        trust(lastSeen = 2.hours.ago())

        repeat(5) {
            tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))
        }

        assertFalse(tracker.device(Peer).first()!!.isWarning)
    }

    @Test
    fun `a device nothing ever reached is not called gone`() = runTest {
        trust(lastSeen = null)

        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))
        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))

        assertFalse(tracker.device(Peer).first()!!.isWarning)
    }

    @Test
    fun `a refusal is reported the first time, whenever the device was last seen`() = runTest {
        trust(lastSeen = 2.hours.ago())

        tracker.recordFailure(Peer, SyncException.RemoteRejectedException("not yours"))

        assertTrue(tracker.device(Peer).first()!!.isWarning)
    }

    @Test
    fun `the run survives the process that started it`() = runTest {
        trust(lastSeen = 30.days.ago())
        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))

        // What a restart amounts to: the same records, a tracker that remembers nothing itself.
        val restarted = ReachabilityTracker(storage, clock)
        restarted.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))

        val failure = restarted.device(Peer).first()!!

        assertEquals(2, failure.attempts)
        assertTrue(failure.isWarning)
    }

    @Test
    fun `a run becomes worth reporting as time passes, with no new attempt`() = runTest {
        trust(lastSeen = clock.now())

        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))
        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))
        assertFalse(tracker.device(Peer).first()!!.isWarning)

        clock.advance(4.days)

        assertTrue(tracker.device(Peer).first()!!.isWarning)
    }

    @Test
    fun `the latest attempt replaces the one before it`() = runTest {
        trust()

        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))
        clock.advance(5.minutes)
        tracker.recordFailure(Peer, SyncException.RemoteRejectedException("not yours"))

        assertEquals(FailedContact.Reason.Refused, reasonOf(Peer))
        assertEquals(1, tracker.failures.first().size)
    }

    @Test
    fun `reaching the device ends the run`() = runTest {
        trust(lastSeen = 30.days.ago())

        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))
        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))
        tracker.recordSuccess(Peer)

        assertNull(tracker.device(Peer).first())
        assertEquals(emptyList<Any>(), tracker.failures.first())

        tracker.recordFailure(Peer, DeviceUnreachableException(Peer, transport = null))

        val failure = tracker.device(Peer).first()!!

        assertEquals(1, failure.attempts)
        assertFalse(failure.isWarning)
    }

    private fun Duration.ago(): Instant = clock.now() - this

    private suspend fun trust(lastSeen: Instant? = null) = storage.trust.upsert(
        TrustedDevice(
            deviceId = Peer,
            displayName = "Peer",
            publicKey = "key-of-$Peer".toByteArray(),
            method = AuthMethod.ConfirmFingerprint,
            strength = "strong",
            metadata = DeviceMetadata(
                kind = null,
                dictionaryId = null,
                dictionaryVersion = null,
                lastSeen = lastSeen,
                lastNetworkId = null,
            ),
        ),
    )

    private suspend fun reasonOf(deviceId: String): FailedContact.Reason? =
        tracker.device(deviceId).first()?.reason

    private companion object {
        const val Peer = "device-peer"
    }
}
