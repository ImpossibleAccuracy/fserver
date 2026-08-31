package com.fserver.files.scan.impl

import com.fserver.files.scan.DirectoryScanProgress
import com.fserver.files.scan.ScannedFile

/**
 * Gathers what a scan found and reports running totals.
 *
 * Synchronized because [ScanSource.Root] walks its volumes in parallel: several scanners call
 * [add] on one collector at once.
 *
 * [onProgress] is invoked outside the lock - it runs host code, so holding the lock across it
 * would serialize every scanner on whatever the caller does with the progress.
 */
internal class ScanCollector(
    private val onProgress: (DirectoryScanProgress) -> Unit,
) {
    private val lock = Any()
    private val found = mutableListOf<ScannedFile>()
    private var totalBytes = 0L

    fun add(file: ScannedFile) {
        val progress = synchronized(lock) {
            found += file
            totalBytes += file.size

            DirectoryScanProgress(
                scannedFiles = found.size,
                scannedSizeBytes = totalBytes,
            )
        }

        onProgress(progress)
    }

    fun result(): List<ScannedFile> = synchronized(lock) { found.toList() }
}
