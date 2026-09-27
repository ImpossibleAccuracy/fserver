package com.fserver.core.sync.conflict

import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictCopiesTest {

    @Test
    fun `the device label goes before the extension, in the same directory`() {
        assertEquals("docs/Report (Pixel 8).docx", ConflictCopies.path("docs/Report.docx", "Pixel 8"))
    }

    @Test
    fun `a taken name is numbered`() {
        assertEquals("Report (Pixel 8 2).docx", ConflictCopies.path("Report.docx", "Pixel 8", n = 2))
    }

    @Test
    fun `a hidden file keeps its leading dot as part of the name`() {
        assertEquals(".bashrc (Pixel 8)", ConflictCopies.path(".bashrc", "Pixel 8"))
    }

    @Test
    fun `a separator in the label cannot move the copy into another directory`() {
        assertEquals("a/Notes (A_B).txt", ConflictCopies.path("a/Notes.txt", "A/B"))
    }
}
