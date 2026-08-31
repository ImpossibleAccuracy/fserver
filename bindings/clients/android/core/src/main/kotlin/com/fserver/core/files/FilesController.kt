package com.fserver.core.files

import com.fserver.core.files.impl.toCore
import com.fserver.core.files.impl.toFiles
import com.fserver.core.files.scan.DirectoryScanProgress
import com.fserver.core.files.scan.ScanSource
import com.fserver.core.files.scan.ScannedFile
import com.fserver.files.FilesNode
import com.fserver.files.model.FileSystemException as FilesFileSystemException

class FilesController internal constructor(
    private val node: FilesNode,
) {
    suspend fun loadContent(
        directory: ScanSource,
        onProgress: (DirectoryScanProgress) -> Unit = {},
    ): Result<List<ScannedFile>> = runCatching {
        try {
            node
                .scanner
                .scan(
                    directory = directory.toFiles(),
                    onProgress = { onProgress(it.toCore()) },
                )
                .map { it.toCore() }
        } catch (e: FilesFileSystemException) {
            throw e.toCore()
        }
    }
}
