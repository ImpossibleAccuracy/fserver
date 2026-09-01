package com.fserver.files.scan

data class FoundFile(
    val path: String,
    val directory: String,
    val size: Long,
)
