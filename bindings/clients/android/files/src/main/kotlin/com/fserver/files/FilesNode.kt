package com.fserver.files

import android.content.Context
import com.fserver.files.scan.ScanSource
import com.fserver.files.scan.DirectoryScanner
import com.fserver.files.scan.FoundFile
import com.fserver.files.scan.impl.DirectoryScannerImpl

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
    val scanner: DirectoryScanner by lazy { DirectoryScannerImpl(context) }

    companion object {
        fun create(context: Context): FilesNode = FilesNode(
            context = context,
        )
    }
}
