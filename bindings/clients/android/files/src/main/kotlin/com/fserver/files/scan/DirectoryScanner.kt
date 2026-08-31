package com.fserver.files.scan

import com.fserver.common.task.ProgressTask

interface DirectoryScanner {
    fun scan(directory: ScanSource): ProgressTask<DirectoryScanProgress, List<ScannedFile>>
}
