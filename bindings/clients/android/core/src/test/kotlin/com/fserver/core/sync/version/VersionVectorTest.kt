package com.fserver.core.sync.version

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionVectorTest {

    @Test
    fun `bump counts one more edit by that device only`() {
        val vector = VersionVector(mapOf(A to 2L, B to 1L)).bump(A)

        assertEquals(VersionVector(mapOf(A to 3L, B to 1L)), vector)
    }

    @Test
    fun `bump by a new device starts its count at one`() {
        assertEquals(VersionVector(mapOf(A to 1L)), VersionVector.Empty.bump(A))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero counters are rejected, so equal histories are equal values`() {
        VersionVector(mapOf(A to 0L))
    }

    private companion object {
        const val A = "device-a"
        const val B = "device-b"
    }
}
