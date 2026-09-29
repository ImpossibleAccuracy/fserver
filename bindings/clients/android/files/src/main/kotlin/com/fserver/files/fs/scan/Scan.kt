package com.fserver.files.fs.scan

import com.fserver.common.task.ProgressTask
import com.fserver.common.task.map
import com.fserver.common.task.progressTask
import kotlinx.coroutines.channels.ProducerScope

/**
 * A scan task over [walk], which calls its callback for each file found. Progress tallying is
 * shared here, so a backend only walks its own source.
 */
internal fun scanTask(
    walk: suspend (onFileFound: (FoundFile) -> Unit) -> Unit,
): ProgressTask<ScanProgress, List<FoundFile>> =
    treeScanTask { onFileFound, _ -> walk(onFileFound) }
        .map(progressMapper = { it }, resultMapper = { it.files })

/** [scanTask] for a backend that also reports the directories it walks through. */
internal fun treeScanTask(
    walk: suspend (
        onFileFound: (FoundFile) -> Unit,
        onDirectoryFound: (FoundDirectory) -> Unit,
    ) -> Unit,
): ProgressTask<ScanProgress, ScanTree> = progressTask {
    val collector = ScanCollector(this)

    walk(collector::add, collector::addDirectory)

    collector.result()
}

/** Helper to collect files and report progress. Thread-safe. */
private class ScanCollector(
    private val scope: ProducerScope<ScanProgress>,
) {
    private val lock = Any()
    private val found = mutableListOf<FoundFile>()
    private val directories = mutableListOf<FoundDirectory>()
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

    fun addDirectory(directory: FoundDirectory) {
        synchronized(lock) { directories += directory }
    }

    fun result(): ScanTree = synchronized(lock) { ScanTree(found.toList(), directories.toList()) }
}
