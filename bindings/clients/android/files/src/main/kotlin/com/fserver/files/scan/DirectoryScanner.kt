package com.fserver.files.scan

import com.fserver.common.task.ProgressTask
import com.fserver.files.model.ScanSource

interface DirectoryScanner {
    fun scan(directory: ScanSource): ProgressTask<DirectoryScanProgress, List<ScannedFile>>
}
