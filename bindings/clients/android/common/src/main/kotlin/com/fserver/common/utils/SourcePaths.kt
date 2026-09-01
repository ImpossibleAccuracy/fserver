package com.fserver.common.utils

import java.security.MessageDigest
import java.text.Normalizer

/**
 * Source-relative paths, in the one form both sides of a sync agree on.
 *
 * Two devices keep the same file in different directories, so a device-local locator - a content
 * URI, an absolute path, a MediaStore row id - can never be compared across them. Everything
 * cross-device is derived from the canonical form here instead.
 */
object SourcePaths {
    /** Volume id of the built-in storage, on every device and every source kind. */
    const val PrimaryVolume = "primary"

    private const val HashAlgorithm = "SHA-256"

    /**
     * Canonical source-relative path: `/` separators, no empty segments, NFC.
     *
     * [volume] leads the path when the source spans several of them, so two files with the same
     * name on internal storage and on an SD card stay distinct. Sources scoped to one directory
     * pass `null`.
     *
     * NFC matters because this is a cross-platform system: APFS hands out decomposed filenames, so
     * an accented name would otherwise hash differently on macOS and on Android. Case is kept as
     * written - a case-insensitive peer may still fold two names into one.
     */
    fun canonical(volume: String?, segments: List<String>): String {
        val parts = ArrayList<String>(segments.size + 1)
        volume?.let { parts += it }

        for (segment in segments) {
            for (part in segment.split('/', '\\')) {
                if (part.isEmpty() || part == ".") continue

                parts += Normalizer.normalize(part, Normalizer.Form.NFC)
            }
        }

        return parts.joinToString(separator = "/")
    }

    fun canonical(volume: String?, path: String): String = canonical(volume, listOf(path))

    /**
     * Cross-device file identity: two devices holding the same file derive the same value.
     *
     * Derived from [canonicalPath] alone - never from a source or device id, which are local to
     * one side and would never match the peer's. Pinned to sha-256 hex because the server and the
     * other clients derive it the same way: changing this is a protocol change, not an
     * Android-local one.
     */
    fun fileId(canonicalPath: String): String =
        MessageDigest.getInstance(HashAlgorithm)
            .digest(canonicalPath.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
}
