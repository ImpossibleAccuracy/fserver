package com.fserver.files.fs.impl

import com.fserver.common.exception.FileSystemException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one check standing between a peer's path and a backend that would obey it.
 */
class SegmentsOfTest {

    @Test
    fun `a canonical path splits into its segments`() {
        assertEquals(listOf("primary", "DCIM", "a.jpg"), segmentsOf("primary/DCIM/a.jpg"))
    }

    @Test
    fun `empty and current-directory segments are dropped`() {
        assertEquals(listOf("a", "b"), segmentsOf("/a//./b/"))
    }

    @Test
    fun `backslashes separate too, so a windows peer cannot smuggle a segment past us`() {
        assertEquals(listOf("a", "..", "b"), "a\\..\\b".split('/', '\\'))

        val failure = runCatching { segmentsOf("a\\..\\b") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a parent segment is refused`() {
        val failure = runCatching { segmentsOf("primary/../../etc/passwd") }.exceptionOrNull()

        assertTrue(failure is FileSystemException.InvalidPath)
    }

    @Test
    fun `a path with nothing in it is refused`() {
        assertTrue(runCatching { segmentsOf("") }.exceptionOrNull() is FileSystemException.InvalidPath)
        assertTrue(runCatching { segmentsOf("/") }.exceptionOrNull() is FileSystemException.InvalidPath)
        assertTrue(runCatching { segmentsOf("/./") }.exceptionOrNull() is FileSystemException.InvalidPath)
    }
}
