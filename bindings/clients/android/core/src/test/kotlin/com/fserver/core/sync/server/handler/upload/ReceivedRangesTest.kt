package com.fserver.core.sync.server.handler.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceivedRangesTest {

    @Test
    fun `the prefix grows only once the gap in front is filled`() {
        val ranges = ReceivedRanges()

        ranges.add(10, 20)
        assertEquals(0, ranges.prefix)

        ranges.add(0, 10)
        assertEquals(20, ranges.prefix)
        assertEquals(20, ranges.total)
    }

    @Test
    fun `overlapping and touching ranges merge into one`() {
        val ranges = ReceivedRanges()

        ranges.add(0, 5)
        ranges.add(8, 12)
        ranges.add(20, 30)
        ranges.add(3, 21)

        assertEquals(30, ranges.prefix)
        assertEquals(30, ranges.total)
    }

    @Test
    fun `covers only what is held end to end`() {
        val ranges = ReceivedRanges(prefix = 10)
        ranges.add(15, 20)

        assertTrue(ranges.covers(2, 10))
        assertTrue(ranges.covers(15, 20))
        assertFalse(ranges.covers(8, 16))
        assertFalse(ranges.covers(10, 11))
    }
}
