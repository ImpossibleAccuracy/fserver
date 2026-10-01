package com.fserver.app.domain.documents

import com.fserver.common.model.FileSize
import com.fserver.core.files.SyncFileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class DocumentTreeTest {
    private val entries = listOf(
        entry("a.jpg"),
        entry("DCIM/b.jpg"),
        entry("DCIM/Camera/c.jpg"),
        entry("DCIMx/d.jpg"),
    )

    @Test
    fun `root lists top-level files and the folders implied by deeper paths`() {
        val children = childrenOf("", entries)

        assertEquals(
            listOf(DocumentNode.Folder("DCIM"), DocumentNode.Folder("DCIMx"), DocumentNode.File(entries[0])),
            children,
        )
    }

    @Test
    fun `a folder lists only what is below it, not a sibling sharing its prefix`() {
        val children = childrenOf("DCIM", entries)

        assertEquals(listOf(DocumentNode.Folder("DCIM/Camera"), DocumentNode.File(entries[1])), children)
    }

    @Test
    fun `nodeAt tells a file from an implied folder from nothing`() {
        assertEquals(DocumentNode.File(entries[1]), nodeAt("DCIM/b.jpg", entries))
        assertEquals(DocumentNode.Folder("DCIM/Camera"), nodeAt("DCIM/Camera", entries))
        assertNull(nodeAt("DCIM/Cam", entries))
    }

    @Test
    fun `ids round-trip and child checks stay within one source`() {
        val id = DocumentIds.of("src", "DCIM/b.jpg")

        assertEquals("src", DocumentIds.sourceOf(id))
        assertEquals("DCIM/b.jpg", DocumentIds.pathOf(id))
        assertEquals("", DocumentIds.pathOf(DocumentIds.of("src", "")))

        assertTrue(DocumentIds.isChild("src", id))
        assertTrue(DocumentIds.isChild("src/DCIM", id))
        assertFalse(DocumentIds.isChild("src/DCIMx", id))
        assertFalse(DocumentIds.isChild("other", id))
        assertFalse(DocumentIds.isChild(id, id))
    }

    private fun entry(path: String) = SyncFileEntry(
        fileId = path,
        sourceId = "src",
        path = path,
        locator = "/storage/$path",
        size = FileSize(1),
        localState = null,
        remoteState = null,
        modifiedAt = Instant.fromEpochMilliseconds(0),
    )
}
