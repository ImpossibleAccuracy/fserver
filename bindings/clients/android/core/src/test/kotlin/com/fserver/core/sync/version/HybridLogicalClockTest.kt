package com.fserver.core.sync.version

import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class HybridLogicalClockTest {

    private val time = MutableTimeProvider()
    private val storage = FakeStorage(localDeviceId = "device-local", clock = time)

    @Test
    fun `readings keep growing while wall time stands still`() = runTest {
        val clock = HybridLogicalClock(storage, time)

        val readings = clock.ticks(3) + clock.now()

        assertEquals(readings.sorted(), readings)
        assertEquals(readings.size, readings.toSet().size)
    }

    @Test
    fun `last reading is persisted`() = runTest {
        val reading = HybridLogicalClock(storage, time).now()

        assertEquals(reading, storage.preferences.clock)
    }

    @Test
    fun `a restarted clock continues after the persisted reading even if wall time went back`() =
        runTest {
            time.advance(60.seconds)
            val before = HybridLogicalClock(storage, time).now()

            time.advance((-120).seconds)
            val after = HybridLogicalClock(storage, time).now()

            assertTrue(after > before)
        }

    @Test
    fun `local readings order after a received one`() = runTest {
        val clock = HybridLogicalClock(storage, time)
        val remote = HlcTimestamp.of(time.now().toEpochMilliseconds() + 30_000, 5)

        clock.receive(remote)

        assertTrue(clock.now() > remote)
    }
}
