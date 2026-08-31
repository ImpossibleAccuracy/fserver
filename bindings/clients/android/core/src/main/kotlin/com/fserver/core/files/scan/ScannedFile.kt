package com.fserver.core.files.scan

import com.fserver.common.model.FileSize

data class ScannedFile(
    val path: String,
    val directory: String,
    val size: FileSize,
)
