package com.fserver.core.files.scan

import com.fserver.common.model.FileSize

data class DirectoryScanProgress(
    val scannedFiles: Int,
    val scannedSize: FileSize,
)