package com.fserver.files.fs.impl

import com.fserver.common.exception.FileSystemException
import com.fserver.common.task.ProgressTask
import com.fserver.common.task.progressTask
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.ScanProgress
import kotlinx.coroutines.channels.ProducerScope

/**
 * Base for the backends in this package.
 * Progress tallying is shared here, so an impl only walks its own source.
 */
internal abstract class SystemAdapter : FileSystem {
    final override fun scan(): ProgressTask<ScanProgress, List<FoundFile>> = progressTask {
        val collector = ScanCollector(this)

        scanFiles(collector::add)

        collector.result()
    }

    /** Walk the bound source and call [onFileFound] for each file found. */
    protected abstract suspend fun scanFiles(onFileFound: (FoundFile) -> Unit)
}

/**
 * A canonical path split into segments, with anything that walks out of the source refused.
 *
 * Every path handed to [FileSystem.createFile] came from a peer, so traversal is rejected here
 * rather than left to the backend underneath.
 */
internal fun segmentsOf(path: String): List<String> {
    val segments = path.split('/', '\\').filter { it.isNotEmpty() && it != "." }

    if (segments.isEmpty() || segments.any { it == ".." }) {
        throw FileSystemException.InvalidPath(path)
    }

    return segments
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
