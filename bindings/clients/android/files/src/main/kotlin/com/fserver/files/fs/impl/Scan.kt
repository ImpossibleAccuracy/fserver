package com.fserver.files.fs.impl

import com.fserver.common.task.ProgressTask
import com.fserver.common.task.progressTask
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.ScanProgress
import kotlinx.coroutines.channels.ProducerScope

/**
 * A scan task over [walk], which calls its callback for each file found. Progress tallying is
 * shared here, so a backend only walks its own source.
 */
internal fun scanTask(
    walk: suspend (onFileFound: (FoundFile) -> Unit) -> Unit,
): ProgressTask<ScanProgress, List<FoundFile>> = progressTask {
    val collector = ScanCollector(this)

    walk(collector::add)

    collector.result()
}

/** Helper to collect files and report progress. Thread-safe. */
private class ScanCollector(
    private val scope: ProducerScope<ScanProgress>,
) {
    private val lock = Any()
    private val found = mutableListOf<FoundFile>()
    private var totalBytes = 0L

    fun add(file: FoundFile) {
        val progress = synchronized(lock) {
            found += file
            totalBytes += file.size.bytes

            ScanProgress(
                scannedFiles = found.size,
                scannedSizeBytes = totalBytes,
            )
        }

        scope.trySend(progress)
    }

    fun result(): List<FoundFile> = synchronized(lock) { found.toList() }
}
