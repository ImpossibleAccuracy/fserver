package com.fserver.core.files.impl

import com.fserver.core.files.model.FileSize
import com.fserver.core.files.model.FileSystemException
import com.fserver.core.files.scan.DirectoryScanProgress
import com.fserver.core.files.scan.ScanSource
import com.fserver.core.files.scan.ScannedFile
import com.fserver.files.model.FileSystemException as FilesFileSystemException
import com.fserver.files.model.ScanSource as FilesFoundDirectory
import com.fserver.files.scan.DirectoryScanProgress as FilesScanProgress
import com.fserver.files.scan.ScannedFile as FilesScannedFile

/**
 * Translation between the `:core` models a host sees and the `:files` models the scanner speaks.
 */

internal fun ScanSource.toFiles(): FilesFoundDirectory = when (this) {
    is ScanSource.Root -> FilesFoundDirectory.Root(rootPaths)
    is ScanSource.Tree -> FilesFoundDirectory.Tree(path)
    ScanSource.Media -> FilesFoundDirectory.Media
}

internal fun FilesScannedFile.toCore(): ScannedFile = ScannedFile(
    path = path,
    directory = directory,
    size = FileSize(size),
)

internal fun FilesScanProgress.toCore(): DirectoryScanProgress = DirectoryScanProgress(
    scannedFiles = scannedFiles,
    scannedSize = FileSize(scannedSizeBytes),
)

internal fun FilesFileSystemException.toCore(): FileSystemException = when (this) {
    is FilesFileSystemException.InvalidPath -> FileSystemException.InvalidPath(path)
    is FilesFileSystemException.NotDirectory -> FileSystemException.NotDirectory(path)
}
