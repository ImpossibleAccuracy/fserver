package com.fserver.files.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionsTest {

    @Test
    fun `merge keeps the larger count per device`() {
        val left = VersionVector(mapOf(A to 3L, B to 1L))
        val right = VersionVector(mapOf(B to 4L, C to 2L))

        assertEquals(VersionVector(mapOf(A to 3L, B to 4L, C to 2L)), left.merge(right))
        assertEquals(left.merge(right), right.merge(left))
    }

    @Test
    fun `merged vector is newer than or equal to both sides`() {
        val left = VersionVector(mapOf(A to 1L))
        val right = VersionVector(mapOf(B to 1L))
        val merged = left.merge(right)

        assertEquals(Causality.Newer, merged.compare(left))
        assertEquals(Causality.Newer, merged.compare(right))
        assertEquals(Causality.Equal, merged.compare(merged))
    }

    @Test
    fun `equal vectors compare equal`() {
        val vector = VersionVector(mapOf(A to 2L, B to 1L))

        assertEquals(Causality.Equal, vector.compare(VersionVector(mapOf(B to 1L, A to 2L))))
        assertEquals(Causality.Equal, VersionVector.Empty.compare(VersionVector.Empty))
    }

    @Test
    fun `a later edit on top of what the other side saw is newer`() {
        val base = VersionVector(mapOf(A to 1L))
        val edited = VersionVector(mapOf(A to 1L, B to 1L))

        assertEquals(Causality.Newer, edited.compare(base))
        assertEquals(Causality.Older, base.compare(edited))
    }

    @Test
    fun `a device missing from one side counts as zero edits`() {
        assertEquals(Causality.Newer, VersionVector(mapOf(A to 1L)).compare(VersionVector.Empty))
        assertEquals(Causality.Older, VersionVector.Empty.compare(VersionVector(mapOf(A to 1L))))
    }

    @Test
    fun `edits made apart from one common base are concurrent`() {
        val onA = VersionVector(mapOf(A to 2L))
        val onB = VersionVector(mapOf(A to 1L, B to 1L))

        assertEquals(Causality.Concurrent, onA.compare(onB))
        assertEquals(Causality.Concurrent, onB.compare(onA))
    }

    @Test
    fun `merged version takes the later stamp, whichever side it is on`() {
        val early = FileVersion(VersionVector(mapOf(A to 1L)), hlc = 10, originDevice = A)
        val late = FileVersion(VersionVector(mapOf(B to 1L)), hlc = 20, originDevice = B)

        val expected = FileVersion(VersionVector(mapOf(A to 1L, B to 1L)), hlc = 20, originDevice = B)
        assertEquals(expected, early.merge(late))
        assertEquals(expected, late.merge(early))
    }

    @Test
    fun `equal stamps are broken by device id, so every side merges alike`() {
        val onA = FileVersion(VersionVector(mapOf(A to 1L)), hlc = 10, originDevice = A)
        val onB = FileVersion(VersionVector(mapOf(B to 1L)), hlc = 10, originDevice = B)

        assertEquals(B, onA.merge(onB).originDevice)
        assertEquals(B, onB.merge(onA).originDevice)
    }

    private companion object {
        const val A = "device-a"
        const val B = "device-b"
        const val C = "device-c"
    }
}
