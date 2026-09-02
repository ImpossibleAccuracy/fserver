package com.fserver.files

import android.content.Context
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.FileSystemSource
import com.fserver.files.fs.FileSystemEntryPoint

/**
 * Entry point to `:files`. Build one per process and keep it.
 *
 * This is the module's whole runtime surface: everything else is `internal`, so a consumer picks
 * a [FileSystemSource] and gets [FoundFile]s back, and never learns whether that came from the
 * filesystem, the Storage Access Framework, or MediaStore.
 */
class FilesNode private constructor(
    private val context: Context,
) {
    private val fileSystem: FileSystemEntryPoint by lazy { FileSystemEntryPoint(context) }

    fun openSource(source: FileSystemSource): FileSystem = fileSystem.open(source)

    companion object {
        fun create(context: Context): FilesNode = FilesNode(
            context = context,
        )
    }
}
