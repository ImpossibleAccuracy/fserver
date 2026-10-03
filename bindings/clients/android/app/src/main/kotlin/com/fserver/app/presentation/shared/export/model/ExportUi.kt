package com.fserver.app.presentation.shared.export.model

import androidx.compose.runtime.Immutable

@Immutable
data class ExportUi(
    val files: Int = 0,
    val totalFiles: Int = 0,
    val writtenBytes: Long = 0,
    val totalBytes: Long = 0,
) {
    val fraction: Float?
        get() = if (totalBytes > 0) (writtenBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null

    companion object {
        val Sample = ExportUi(files = 412, totalFiles = 4817, writtenBytes = 9_800_000_000, totalBytes = 58_400_000_000)
    }
}

sealed interface ExportResult {
    data class Finished(val files: Int, val skipped: Int) : ExportResult

    data object Failed : ExportResult
}
