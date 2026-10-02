package com.fserver.core.sync.index

import com.fserver.files.fs.impl.AtomicReplaceMarker
import com.fserver.files.fs.impl.PartMarker

/** Files a scan never indexes: temp and lock files, and OS / app service data. */
internal object IgnoredPaths {
    private val ignoredDirs = setOf(
        ".thumbnails", ".trash", ".trashes", ".spotlight-v100", ".fseventsd", ".temporaryitems",
        ".appledouble", "\$recycle.bin", "system volume information", "lost+found", ".stfolder",
        ".stversions",
    )

    private val ignoredNames = setOf(
        ".nomedia", ".ds_store", "thumbs.db", "ehthumbs.db", "desktop.ini", ".directory",
    )

    // Android trash / pending MediaStore rows, office owner locks, emacs locks, AppleDouble forks.
    private val ignoredPrefixes =
        listOf(".trashed-", ".pending-", ".trash-", "~$", ".~lock.", ".#", "._")

    private val ignoredSuffixes = listOf(
        ".tmp", ".temp", ".part", ".partial", ".crdownload", ".download", ".swp", ".swo", ".lck",
        "~", AtomicReplaceMarker,
    )

    /** [path] is canonical: `/`-separated, see [com.fserver.common.utils.SourcePaths]. */
    fun isIgnored(path: String): Boolean {
        val segments = path.lowercase().split('/')
        val name = segments.last()

        return segments.dropLast(1).any { it in ignoredDirs || it.startsWith(".trash-") } ||
                name in ignoredNames ||
                ignoredPrefixes.any { name.startsWith(it) } ||
                ignoredSuffixes.any { name.endsWith(it) } ||
                // Our own in-flight copy, see `partNameOf` in :files.
                PartMarker in name
    }
}
