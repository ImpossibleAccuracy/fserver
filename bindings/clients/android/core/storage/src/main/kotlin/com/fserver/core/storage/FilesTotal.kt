package com.fserver.core.storage

import com.fserver.common.model.FileSize

data class FilesTotal(
    val count: Int,
    val size: FileSize,
)
