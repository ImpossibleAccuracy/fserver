package com.fserver.files.scan

import com.fserver.files.model.ScanSource

interface DirectoryScanner {
    suspend fun scan(
        directory: ScanSource,
        onProgress: (DirectoryScanProgress) -> Unit,
    ): List<ScannedFile>
}
