package com.fserver.app.data.preview

import android.graphics.Bitmap
import android.os.Build
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import java.io.OutputStream

/**
 * Pictures in a Coil [diskCache], one per key, each stamped with the version of the file it shows.
 * A picture of another version is dropped as soon as it is asked for, so an edited file leaves no
 * stale one behind.
 */
class VersionedImages(val diskCache: DiskCache) {

    /** The picture under [key] made at [version], for the caller to close; null when there is none. */
    fun open(key: String, version: String?): DiskCache.Snapshot? {
        val snapshot = diskCache.openSnapshot(key) ?: return null
        val stored = runCatching { diskCache.fileSystem.read(snapshot.metadata) { readUtf8() } }.getOrNull()
        if (stored == version.orEmpty()) return snapshot

        snapshot.close()
        diskCache.remove(key)
        return null
    }

    fun has(key: String, version: String?): Boolean = open(key, version)?.use { true } ?: false

    fun save(key: String, version: String?, bitmap: Bitmap) {
        write(key, version) { sink -> bitmap.compress(Format, Quality, sink) }
    }

    /** Takes [from]'s picture under [key] as is - no decode, no re-encode. */
    fun copy(from: DiskCache.Snapshot, fromCache: DiskCache, key: String, version: String?) {
        write(key, version) { sink -> fromCache.fileSystem.read(from.data) { inputStream().copyTo(sink) } }
    }

    fun remove(key: String) {
        diskCache.remove(key)
    }

    fun result(snapshot: DiskCache.Snapshot, key: String) = SourceFetchResult(
        source = ImageSource(file = snapshot.data, fileSystem = diskCache.fileSystem, diskCacheKey = key, closeable = snapshot),
        mimeType = MimeType,
        dataSource = DataSource.DISK,
    )

    private fun write(key: String, version: String?, body: (OutputStream) -> Unit) {
        val editor = diskCache.openEditor(key) ?: return
        try {
            diskCache.fileSystem.write(editor.data) { body(outputStream()) }
            diskCache.fileSystem.write(editor.metadata) { writeUtf8(version.orEmpty()) }
            editor.commit()
        } catch (e: Exception) {
            editor.abort()
            throw e
        }
    }

    private companion object {
        const val MimeType = "image/webp"
        const val Quality = 80

        @Suppress("DEPRECATION")
        val Format =
            if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    }
}
