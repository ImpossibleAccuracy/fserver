package com.fserver.core.files.scan

import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.ScanProgress
import com.fserver.files.fs.FileSystemSource

internal fun SourceLocation.toFiles(): FileSystemSource = when (this) {
    is SourceLocation.Root -> FileSystemSource.Root(volumes.map { it.toFiles() })
    is SourceLocation.Tree -> FileSystemSource.Tree(path)
    SourceLocation.Media -> FileSystemSource.Media
    is SourceLocation.Internal -> FileSystemSource.Internal(bucket)
    is SourceLocation.Directory -> FileSystemSource.Directory(path)
}

private fun SourceLocation.Root.Volume.toFiles(): FileSystemSource.Root.Volume =
    FileSystemSource.Root.Volume(id = id, path = path)

internal fun FoundFile.toCore(): ScannedFile = ScannedFile(
    path = path,
    directory = path.substringBeforeLast('/', missingDelimiterValue = ""),
    locator = locator,
    size = size,
    lastModified = lastModified,
)

internal fun ScanProgress.toCore(): DirectoryScanProgress = DirectoryScanProgress(
    scannedFiles = scannedFiles,
    scannedSize = FileSize(scannedSizeBytes),
)
