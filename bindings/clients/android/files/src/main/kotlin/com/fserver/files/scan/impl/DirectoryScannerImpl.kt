package com.fserver.files.scan.impl

import android.content.Context
import android.os.Build
import com.fserver.common.task.ProgressTask
import com.fserver.files.model.ScanSource
import com.fserver.common.task.progressTask
import com.fserver.files.scan.DirectoryScanProgress
import com.fserver.files.scan.DirectoryScanner
import com.fserver.files.scan.ScannedFile
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Dispatch only: one [ScanSource] kind to one scanner in this package, with [ScanCollector]
 * doing the tallying for all of them. Adding a kind is a new scanner file plus a branch here.
 */
internal class DirectoryScannerImpl(
    private val context: Context,
) : DirectoryScanner {
    override fun scan(
        directory: ScanSource,
    ): ProgressTask<DirectoryScanProgress, List<ScannedFile>> = progressTask {
        val collector = ScanCollector(this)

        when (directory) {
            is ScanSource.Root -> scanRoots(directory.rootPaths, collector)

            is ScanSource.Tree -> RecursiveTreeScanner.scanTree(
                context = context,
                dirPath = directory.path,
                onFileFound = collector::add,
            )

            is ScanSource.Media -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStoreScanner.scanMedia(context, collector::add)
            } else {
                TODO("Add files scan for pre-Android 10")
            }
        }

        collector.result()
    }

    /** Volumes are independent trees, so they are walked at the same time. */
    private suspend fun scanRoots(
        rootPaths: List<String>,
        collector: ScanCollector,
    ) = coroutineScope {
        for (path in rootPaths) {
            launch { DirectoryFilesScanner.scanDirectory(path, collector::add) }
        }
    }
}

private class ScanCollector(
    private val scope: ProducerScope<DirectoryScanProgress>,
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

        scope.trySend(progress)
    }

    fun result(): List<ScannedFile> = synchronized(lock) { found.toList() }
}
