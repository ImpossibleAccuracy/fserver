package com.fserver.files.scan.impl

import android.content.Context
import android.os.Build
import com.fserver.common.task.ProgressTask
import com.fserver.files.scan.ScanSource
import com.fserver.common.task.progressTask
import com.fserver.files.scan.ScanProgress
import com.fserver.files.scan.DirectoryScanner
import com.fserver.files.scan.FoundFile
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
    ): ProgressTask<ScanProgress, List<FoundFile>> = progressTask {
        val collector = ScanCollector(this)

        when (directory) {
            is ScanSource.Root -> scanVolumes(directory.volumes, collector)

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
    private suspend fun scanVolumes(
        volumes: List<ScanSource.Root.Volume>,
        collector: ScanCollector,
    ) = coroutineScope {
        for (volume in volumes) {
            launch { DirectoryFilesScanner.scanVolume(volume, collector::add) }
        }
    }
}

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
