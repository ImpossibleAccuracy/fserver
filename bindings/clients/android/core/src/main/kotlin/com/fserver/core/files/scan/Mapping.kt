package com.fserver.core.files.scan

import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.ScanProgress
import com.fserver.files.fs.ScanSource

internal fun SourceLocation.toFiles(): ScanSource = when (this) {
    is SourceLocation.Root -> ScanSource.Root(volumes.map { it.toFiles() })
    is SourceLocation.Tree -> ScanSource.Tree(path)
    SourceLocation.Media -> ScanSource.Media
}

private fun SourceLocation.Root.Volume.toFiles(): ScanSource.Root.Volume =
    ScanSource.Root.Volume(id = id, path = path)

internal fun FoundFile.toCore(): ScannedFile = ScannedFile(
    path = path,
    directory = path.substringBeforeLast('/', missingDelimiterValue = ""),
    size = size,
    lastModified = lastModified,
)

internal fun ScanProgress.toCore(): DirectoryScanProgress = DirectoryScanProgress(
    scannedFiles = scannedFiles,
    scannedSize = FileSize(scannedSizeBytes),
)
