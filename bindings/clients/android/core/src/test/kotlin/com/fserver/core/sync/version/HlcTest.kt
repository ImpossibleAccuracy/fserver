package com.fserver.core.sync.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlcTest {

    @Test
    fun `packing keeps both parts and orders by millis, then counter`() {
        val timestamp = HlcTimestamp.of(physicalMs = 1_700_000_000_000, logical = 42)

        assertEquals(1_700_000_000_000, timestamp.physicalMs)
        assertEquals(42, timestamp.logical)
        assertTrue(HlcTimestamp.of(1000, 5) < HlcTimestamp.of(1000, 6))
        assertTrue(HlcTimestamp.of(1000, HlcTimestamp.MaxLogical) < HlcTimestamp.of(1001, 0))
    }

    @Test
    fun `tick takes wall time when it moved forward`() {
        val last = HlcTimestamp.of(1000, 7)

        assertEquals(HlcTimestamp.of(2000, 0), Hlc.tick(last, physicalMs = 2000))
    }

    @Test
    fun `tick counts up when wall time stands still`() {
        val last = HlcTimestamp.of(1000, 7)

        assertEquals(HlcTimestamp.of(1000, 8), Hlc.tick(last, physicalMs = 1000))
    }

    @Test
    fun `tick never goes back when the wall clock does`() {
        val last = HlcTimestamp.of(5000, 0)

        val next = Hlc.tick(last, physicalMs = 1000)

        assertEquals(HlcTimestamp.of(5000, 1), next)
        assertTrue(next > last)
    }

    @Test
    fun `a full counter carries into the millis instead of repeating`() {
        val last = HlcTimestamp.of(1000, HlcTimestamp.MaxLogical)

        assertEquals(HlcTimestamp.of(1001, 0), Hlc.tick(last, physicalMs = 1000))
    }

    @Test
    fun `receive orders after a peer that is slightly ahead`() {
        val last = HlcTimestamp.of(1000, 3)
        val remote = HlcTimestamp.of(1500, 9)

        val received = Hlc.receive(last, remote, physicalMs = 1200)

        assertEquals(HlcTimestamp.of(1500, 10), received.timestamp)
        assertFalse(received.skewed)
    }

    @Test
    fun `receive from a peer behind us keeps our time`() {
        val last = HlcTimestamp.of(2000, 3)

        val received = Hlc.receive(last, HlcTimestamp.of(1000, 50), physicalMs = 1500)

        assertEquals(HlcTimestamp.of(2000, 4), received.timestamp)
    }

    @Test
    fun `receive at the same millis takes the larger counter`() {
        val last = HlcTimestamp.of(1000, 3)

        val received = Hlc.receive(last, HlcTimestamp.of(1000, 8), physicalMs = 900)

        assertEquals(HlcTimestamp.of(1000, 9), received.timestamp)
    }

    @Test
    fun `receive when wall time is ahead of both starts the counter over`() {
        val received = Hlc.receive(
            last = HlcTimestamp.of(1000, 3),
            remote = HlcTimestamp.of(1100, 8),
            physicalMs = 5000,
        )

        assertEquals(HlcTimestamp.of(5000, 0), received.timestamp)
    }

    @Test
    fun `a peer too far in the future is flagged and its wall time is not adopted`() {
        val physicalMs = 1_000_000L
        val remote = HlcTimestamp.of(physicalMs + Hlc.MaxDriftMs + 1, 0)

        val received = Hlc.receive(HlcTimestamp.of(physicalMs - 10, 0), remote, physicalMs)

        assertTrue(received.skewed)
        assertEquals(physicalMs, received.timestamp.physicalMs)
    }

    @Test
    fun `a peer ahead by exactly the allowed drift is still trusted`() {
        val physicalMs = 1_000_000L
        val remote = HlcTimestamp.of(physicalMs + Hlc.MaxDriftMs, 0)

        val received = Hlc.receive(HlcTimestamp.Zero, remote, physicalMs)

        assertFalse(received.skewed)
        assertEquals(HlcTimestamp.of(physicalMs + Hlc.MaxDriftMs, 1), received.timestamp)
    }

    @Test
    fun `an event after receiving orders after the remote one, whatever our wall clock says`() {
        // Our clock is 30 s behind the peer's: its edit must still read as earlier than ours.
        val remote = HlcTimestamp.of(31_000, 0)
        val received = Hlc.receive(HlcTimestamp.of(1000, 0), remote, physicalMs = 1000)

        val local = Hlc.tick(received.timestamp, physicalMs = 1001)

        assertTrue(local > remote)
    }
}
