package com.fserver.files

import android.content.Context
import com.fserver.files.fs.FileSource
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.ScanSource
import com.fserver.files.fs.SourceFileSystem

/**
 * Entry point to `:files`. Build one per process and keep it.
 *
 * This is the module's whole runtime surface: everything else is `internal`, so a consumer picks
 * a [ScanSource] and gets [FoundFile]s back, and never learns whether that came from the
 * filesystem, the Storage Access Framework, or MediaStore.
 */
class FilesNode private constructor(
    private val context: Context,
) {
    private val fileSystem: SourceFileSystem by lazy { SourceFileSystem(context) }

    fun openSource(source: ScanSource): FileSource = fileSystem.open(source)

    companion object {
        fun create(context: Context): FilesNode = FilesNode(
            context = context,
        )
    }
}
