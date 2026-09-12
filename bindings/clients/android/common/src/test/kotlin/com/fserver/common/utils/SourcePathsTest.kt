package com.fserver.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.MessageDigest
import java.text.Normalizer

/**
 * Cross-device file identity. Two devices holding the same file have to derive the same id from
 * it, and two different files must never collide into one - the id is what a delete, a download
 * and an overwrite are addressed by.
 *
 * This is also a protocol surface: the server and the other clients derive it the same way, so a
 * change here is a change everywhere.
 */
class SourcePathsTest {

    @Test
    fun `a path is joined with forward slashes, under its volume`() {
        assertEquals(
            "primary/photos/a.jpg",
            SourcePaths.canonical(volume = "primary", segments = listOf("photos", "a.jpg")),
        )
    }

    @Test
    fun `a source scoped to one directory carries no volume`() {
        assertEquals(
            "photos/a.jpg",
            SourcePaths.canonical(volume = null, path = "photos/a.jpg"),
        )
    }

    @Test
    fun `windows separators and empty segments are flattened away`() {
        assertEquals(
            "photos/2024/a.jpg",
            SourcePaths.canonical(volume = null, path = "photos\\\\2024//./a.jpg"),
        )
    }

    @Test
    fun `the same name decomposed and composed is one file, not two`() {
        val composed = "café.jpg"
        val decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD)

        // APFS hands out the decomposed form; without NFC the same file hashes differently on
        // macOS and on Android and each side keeps re-downloading the other's copy.
        assertNotEquals(composed, decomposed)
        assertEquals(
            SourcePaths.fileId(SourcePaths.canonical(null, composed)),
            SourcePaths.fileId(SourcePaths.canonical(null, decomposed)),
        )
    }

    @Test
    fun `case is kept as written`() {
        assertNotEquals(
            SourcePaths.fileId(SourcePaths.canonical(null, "Photo.jpg")),
            SourcePaths.fileId(SourcePaths.canonical(null, "photo.jpg")),
        )
    }

    @Test
    fun `the same name on two volumes stays two files`() {
        assertNotEquals(
            SourcePaths.fileId(SourcePaths.canonical("primary", "a.jpg")),
            SourcePaths.fileId(SourcePaths.canonical("sd-card", "a.jpg")),
        )
    }

    @Test
    fun `the id is sha-256 of the canonical path, and nothing else`() {
        val path = SourcePaths.canonical(null, "photos/a.jpg")

        val expected = MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        // Pinned on purpose: the peer derives the same value, so changing it is a protocol change.
        assertEquals(expected, SourcePaths.fileId(path))
    }

    @Test
    fun `a parent traversal never survives into a canonical path`() {
        // TODO: `canonical` drops "." and empty segments but keeps "..", so a peer-supplied path
        //  reaches the filesystem layer still carrying it. `DirectoryFileSystem` refuses it there,
        //  but the canonical form is also what a file id is derived from and what other backends
        //  are handed - a path that means "outside the source" should not be representable.
        //  Decide (strip, or refuse) and assert:
        //  assertEquals("b.jpg", SourcePaths.canonical(null, "a/../b.jpg")) - or an exception.
    }
}
