package com.fserver.files.fs.impl

import com.fserver.common.task.ProgressTask
import com.fserver.common.task.progressTask
import com.fserver.files.fs.FileSource
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.ScanProgress
import kotlinx.coroutines.channels.ProducerScope

/**
 * Base for the backends in this package.
 * Progress tallying is shared here, so an impl only walks its own source.
 */
internal abstract class SourceAdapter : FileSource {
    final override fun scan(): ProgressTask<ScanProgress, List<FoundFile>> = progressTask {
        val collector = ScanCollector(this)

        scanFiles(collector::add)

        collector.result()
    }

    /** Walk the bound source and call [onFileFound] for each file found. */
    protected abstract suspend fun scanFiles(onFileFound: (FoundFile) -> Unit)
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
