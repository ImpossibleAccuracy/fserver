package com.fserver.core.files

import com.fserver.core.files.model.DirectoryScanProgress
import com.fserver.core.files.model.FoundDirectory
import com.fserver.core.files.scan.DirectoryScanner
import com.fserver.core.files.scan.ScannedFile

class FilesController internal constructor(
    private val directoryScanner: DirectoryScanner,
) {
    suspend fun loadContent(
        directory: FoundDirectory,
        onProgress: (DirectoryScanProgress) -> Unit = {},
    ): Result<List<ScannedFile>> = runCatching {
        directoryScanner.scan(
            directory = directory,
            onProgress = onProgress,
        )
    }
}
