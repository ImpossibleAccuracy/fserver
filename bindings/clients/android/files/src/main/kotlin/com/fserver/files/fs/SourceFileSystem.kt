package com.fserver.files.fs

import android.content.Context
import android.os.Build
import com.fserver.files.fs.impl.MediaFileSource
import com.fserver.files.fs.impl.RootFileSource
import com.fserver.files.fs.impl.TreeFileSource

/**
 * Binds a [ScanSource] to the backend that can serve it — the only place that knows which kinds
 * exist. Adding a kind is a new [FileSource] impl plus a branch here; the operations on
 * [FileSource] stay branch-free.
 */
internal class SourceFileSystem(
    private val context: Context,
) {
    fun open(source: ScanSource): FileSource = when (source) {
        is ScanSource.Root -> RootFileSource(source)
        is ScanSource.Tree -> TreeFileSource(context, source)
        is ScanSource.Media -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaFileSource(context)
        } else {
            TODO("Add files scan for pre-Android 10")
        }
    }
}
