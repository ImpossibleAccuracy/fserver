package com.fserver.app.data.preview

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.io.FileOutputStream

/**
 * Previews kept for evicted files, one per file. Outside the cache directory: the stub has
 * nothing else to show, so clearing cache must not take them.
 */
class EvictionPreviews(context: Context) {
    private val directory by lazy { context.noBackupFilesDir.resolve(Directory) }

    /** Bytes all kept previews take. */
    internal fun bytes(): Long = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    internal fun clear() {
        directory.deleteRecursively()
    }

    internal fun save(sourceId: String, fileId: String, bitmap: Bitmap) {
        directory.mkdirs()
        val target = fileFor(sourceId, fileId)
        val temp = File.createTempFile(target.name, ".tmp", directory)

        try {
            val written = FileOutputStream(temp).use { out ->
                bitmap.compress(Format, Quality, out).also { if (it) out.fd.sync() }
            }
            if (written && temp.renameTo(target)) return
        } finally {
            temp.delete()
        }
    }

    /** Where the preview of this file is, or would be. Hashed: ids need not be safe as file names. */
    fun fileFor(sourceId: String, fileId: String): File =
        File(directory, "$sourceId/$fileId".encodeUtf8().sha256().hex() + ".webp")

    private companion object {
        const val Directory = "eviction_previews"
        const val Quality = 80

        @Suppress("DEPRECATION")
        val Format =
            if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    }
}
