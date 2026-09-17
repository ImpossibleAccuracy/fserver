package com.fserver.files.fs

import android.content.Context
import android.os.Build
import com.fserver.files.fs.impl.DirectoryFileSystem
import com.fserver.files.fs.impl.LegacyMediaFileSystem
import com.fserver.files.fs.impl.MediaFileSystem
import com.fserver.files.fs.impl.RootFileSystem
import com.fserver.files.fs.impl.TreeFileSystem
import java.io.File

/**
 * Binds a [FileSystemSource] to the backend that can serve it — the only place that knows which kinds
 * exist. Adding a kind is a new [FileSystem] impl plus a branch here; the operations on
 * [FileSystem] stay branch-free.
 */
internal class FileSystemEntryPoint(
    private val context: Context,
) {
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
    }
}
