package com.fserver.core.files.scan

import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.files.fs.FileSystemSource
import com.fserver.files.fs.scan.FoundFile
import com.fserver.files.fs.scan.ScanProgress
import com.fserver.files.fs.scan.ScanTree

internal fun SourceLocation.toFiles(): FileSystemSource = when (this) {
    is SourceLocation.Root -> FileSystemSource.Root(volumes.map { it.toFiles() })
    is SourceLocation.Tree -> FileSystemSource.Tree(path)
    SourceLocation.Media -> FileSystemSource.Media
    is SourceLocation.Internal -> FileSystemSource.Internal(bucket)
    is SourceLocation.Downloads -> FileSystemSource.Downloads(directory)
    is SourceLocation.Directory -> FileSystemSource.Directory(path)
}

private fun SourceLocation.Root.Volume.toFiles(): FileSystemSource.Root.Volume =
    FileSystemSource.Root.Volume(id = id, path = path)

internal fun FoundFile.toCore(): ScannedContent.File = ScannedContent.File(
    path = path,
    directory = path.substringBeforeLast('/', missingDelimiterValue = ""),
    locator = locator,
    size = size,
    lastModified = lastModified,
)

internal fun ScanTree.toCore(): ScannedContent = ScannedContent(
    files = files.map { it.toCore() },
    directories = directories.map {
        ScannedContent.Directory(
            path = it.path,
            locator = it.locator
        )
    },
)

internal fun ScanProgress.toCore(): DirectoryScanProgress = DirectoryScanProgress(
    scannedFiles = scannedFiles,
    scannedSize = FileSize(scannedSizeBytes),
)
