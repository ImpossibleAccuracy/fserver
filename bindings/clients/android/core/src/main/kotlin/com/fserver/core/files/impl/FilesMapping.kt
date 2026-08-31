package com.fserver.core.files.impl

import com.fserver.common.model.FileSize
import com.fserver.core.files.scan.DirectoryScanProgress
import com.fserver.core.files.scan.ScanSource
import com.fserver.core.files.scan.ScannedFile
import com.fserver.files.model.ScanSource as FilesScanSource
import com.fserver.files.scan.DirectoryScanProgress as FilesScanProgress
import com.fserver.files.scan.ScannedFile as FilesScannedFile

internal fun ScanSource.toFiles(): FilesScanSource = when (this) {
    is ScanSource.Root -> FilesScanSource.Root(rootPaths)
    is ScanSource.Tree -> FilesScanSource.Tree(path)
    ScanSource.Media -> FilesScanSource.Media
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
