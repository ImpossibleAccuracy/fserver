package com.fserver.app.data.preview

import android.content.Context
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath

/**
 * Previews kept for evicted files, apart from Coil's own cache. Outside the cache directory: the
 * stub has nothing else to show, so clearing cache must not take them, and thumbnails of files
 * still here must not crowd them out. Plaintext even for an encrypted source - a downsampled
 * picture is not the file.
 */
class EvictionPreviews(private val context: Context) {
    val images: VersionedImages by lazy {
        LegacyDirectories.forEach { context.noBackupFilesDir.resolve(it).deleteRecursively() }

        VersionedImages(
            DiskCache.Builder()
                .directory(context.noBackupFilesDir.resolve(Directory).toOkioPath())
                .maxSizePercent(MaxShare)
                .build(),
        )
    }

    val diskCache: DiskCache get() = images.diskCache

    private companion object {
        const val Directory = "previews"
        val LegacyDirectories = listOf("eviction_previews", "image_cache")

        /** Generous: unlike a thumbnail, a preview cannot be made again once the bytes are gone. */
        const val MaxShare = 0.05
    }
}
