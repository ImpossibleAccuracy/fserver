package com.fserver.files.fs

import android.content.Context
import android.os.Build
import com.fserver.files.fs.impl.local.DirectoryFileSystem
import com.fserver.files.fs.impl.local.RootFileSystem
import com.fserver.files.fs.impl.media.DownloadsFileSystem
import com.fserver.files.fs.impl.media.LegacyMediaFileSystem
import com.fserver.files.fs.impl.media.MediaFileSystem
import com.fserver.files.fs.impl.shared.SharedFileSystem
import com.fserver.files.fs.impl.tree.TreeFileSystem
import java.io.File

/**
 * Binds a [ReadableSource] to the backend that can serve it — the only place that knows which kinds
 * exist. Adding a kind is a new [FileSystem] impl plus a branch here; the operations on
 * [FileSystem] stay branch-free.
 */
internal class FileSystemEntryPoint(
    private val context: Context,
) {
    fun open(source: ReadableSource): ReadableFileSystem = when (source) {
        is FileSystemSource -> open(source)
        is ReadableSource.Shared -> SharedFileSystem(context, source.uris)
    }

    fun open(source: FileSystemSource): FileSystem = when (source) {
        is FileSystemSource.Root -> RootFileSystem(source)
        is FileSystemSource.Tree -> TreeFileSystem(context, source)
        is FileSystemSource.Internal -> DirectoryFileSystem.internal(context, source.bucket)
        is FileSystemSource.Directory -> DirectoryFileSystem(File(source.path))

        // Scoped storage split this one in two: the same source, reached through the provider on
        // Android 10 and up and through the paths its rows still carry below it.
        is FileSystemSource.Media -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaFileSystem(context)
        } else {
            LegacyMediaFileSystem(context)
        }

        // Same split: MediaStore.Downloads arrived with scoped storage.
        is FileSystemSource.Downloads -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DownloadsFileSystem(context, source.directory)
        } else {
            DirectoryFileSystem.legacyDownloads(context, source.directory)
        }
    }
}
