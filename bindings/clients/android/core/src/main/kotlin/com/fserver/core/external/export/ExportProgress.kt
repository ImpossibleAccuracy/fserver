package com.fserver.core.external.export

import com.fserver.common.model.FileSize

/** How far [DataExport.export] got. Totals count only the files whose bytes go into the archive. */
data class ExportProgress(
    val files: Int,
    val totalFiles: Int,
    val written: FileSize,
    val totalSize: FileSize,
)

/** What ended up in the archive. */
data class ExportReport(
    /** Files whose bytes are in the archive. */
    val files: Int,
    val size: FileSize,
    /** Files described by metadata only: evicted here, or held by the peer alone. */
    val metadataOnly: Int,
    /** Files held here that could not be read; their metadata is still in the archive. */
    val skipped: List<SkippedFile>,
) {
    data class SkippedFile(
        val sourceId: String,
        val path: String,
        val reason: String,
    )
}
