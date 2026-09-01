package com.fserver.files.scan.impl

import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths
import com.fserver.files.scan.FoundFile
import com.fserver.files.scan.ScanSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Instant

internal object DirectoryFilesScanner {
    suspend fun scanVolume(
        volume: ScanSource.Root.Volume,
        onFileFound: (FoundFile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val root = File(volume.path)

        if (!root.exists()) throw FileSystemException.NotDirectory(volume.path)
        if (!root.isDirectory) throw FileSystemException.NotDirectory(volume.path)

        for (item in root.walkTopDown()) {
            currentCoroutineContext().ensureActive()

            if (!item.isFile) continue

            onFileFound(
                FoundFile(
                    path = SourcePaths.canonical(
                        volume = volume.id,
                        path = item.relativeTo(root).invariantSeparatorsPath,
                    ),
                    size = FileSize(item.length()),
                    lastModified = Instant.fromEpochMilliseconds(item.lastModified()),
                    provider = FileContentProvider(item),
                )
            )
        }
    }
}
