package com.fserver.core.files.model

data class DirectoryScanProgress(
    val scannedFiles: Int,
    val scannedSize: FileSize,
)