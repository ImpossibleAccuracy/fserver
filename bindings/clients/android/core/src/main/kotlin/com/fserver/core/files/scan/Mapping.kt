package com.fserver.core.files.scan

import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.files.scan.FoundFile
import com.fserver.files.scan.ScanProgress
import com.fserver.files.scan.ScanSource

internal fun SourceLocation.toFiles(): ScanSource = when (this) {
    is SourceLocation.Root -> ScanSource.Root(rootPaths)
    is SourceLocation.Tree -> ScanSource.Tree(path)
    SourceLocation.Media -> ScanSource.Media
}

internal fun FoundFile.toCore(): ScannedFile = ScannedFile(
    path = path,
    directory = directory,
    size = FileSize(size),
)

internal fun ScanProgress.toCore(): DirectoryScanProgress = DirectoryScanProgress(
    scannedFiles = scannedFiles,
    scannedSize = FileSize(scannedSizeBytes),
)
