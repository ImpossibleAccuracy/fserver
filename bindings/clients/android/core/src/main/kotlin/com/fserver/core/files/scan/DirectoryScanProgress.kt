package com.fserver.core.files.scan

import com.fserver.core.files.model.FileSize

data class DirectoryScanProgress(
    val scannedFiles: Int,
    val scannedSize: FileSize,
)