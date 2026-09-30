package com.fserver.core.sync.server.handler.upload.oneshot

import org.junit.Assert.assertEquals
import org.junit.Test

class OneShotNamesTest {

    @Test
    fun `a peer's name never becomes a path`() {
        assertEquals("evil.sh", safeFileName("../../evil.sh"))
        assertEquals("evil.sh", safeFileName("..\\..\\evil.sh"))
        assertEquals("bashrc", safeFileName(".bashrc"))
        assertEquals("ab.txt", safeFileName("a\u0000b.txt"))
        assertEquals("file", safeFileName(".."))
        assertEquals("file", safeFileName(""))
        assertEquals(200, safeFileName("x".repeat(1000)).length)
    }
}
