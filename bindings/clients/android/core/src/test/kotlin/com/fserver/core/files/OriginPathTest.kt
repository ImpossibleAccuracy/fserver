package com.fserver.core.files

import org.junit.Assert.assertEquals
import org.junit.Test

class OriginPathTest {
    private val volumes = listOf(
        SourceLocation.Root.Volume(id = "primary", path = "/storage/emulated/0"),
        SourceLocation.Root.Volume(id = "1234-ABCD", path = "/storage/1234-ABCD"),
    )

    @Test
    fun `directory drops its volume mount point`() {
        assertEquals("dir", SourceLocation.Directory("/storage/emulated/0/dir").toOriginPath(volumes))
        assertEquals(
            "DCIM/Camera",
            SourceLocation.Directory("/storage/1234-ABCD/DCIM/Camera/").toOriginPath(volumes),
        )
    }

    @Test
    fun `directory at a volume root is named by the volume`() {
        assertEquals("primary", SourceLocation.Directory("/storage/emulated/0").toOriginPath(volumes))
    }

    @Test
    fun `directory on no known volume keeps its path`() {
        assertEquals("data/dir", SourceLocation.Directory("/data/dir").toOriginPath(volumes))
        assertEquals("storage/emulated/01", SourceLocation.Directory("/storage/emulated/01").toOriginPath(volumes))
    }

    @Test
    fun `tree drops its volume prefix`() {
        val tree = SourceLocation.Tree(
            "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera"
        )

        assertEquals("DCIM/Camera", tree.toOriginPath(volumes))
    }
}
