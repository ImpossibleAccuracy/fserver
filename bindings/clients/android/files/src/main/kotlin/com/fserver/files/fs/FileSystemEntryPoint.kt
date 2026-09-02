package com.fserver.files.fs

import android.content.Context
import android.os.Build
import com.fserver.files.fs.impl.MediaFileSystem
import com.fserver.files.fs.impl.RootFileSystem
import com.fserver.files.fs.impl.TreeFileSystem

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
        is FileSystemSource.Media -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaFileSystem(context)
        } else {
            TODO("Add files scan for pre-Android 10")
        }
    }
}
