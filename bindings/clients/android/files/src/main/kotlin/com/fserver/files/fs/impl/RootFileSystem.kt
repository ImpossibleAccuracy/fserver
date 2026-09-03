package com.fserver.files.fs.impl

import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.FileSystemSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.time.Instant

internal class RootFileSystem(
    private val source: FileSystemSource.Root,
) : SystemAdapter() {
    override suspend fun scanFiles(
        onFileFound: (FoundFile) -> Unit,
    ) = coroutineScope {
        for (volume in source.volumes) {
            launch { scanVolume(volume, onFileFound) }
        }
    }

    private suspend fun scanVolume(
        volume: FileSystemSource.Root.Volume,
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
                    locator = item.absolutePath,
                    size = FileSize(item.length()),
                    lastModified = Instant.fromEpochMilliseconds(item.lastModified()),
                )
            )
        }
    }

    override suspend fun createFile(path: String): String {
        TODO("Not yet implemented")
    }

    override suspend fun openFile(locator: String): InputStream {
        val file = File(locator)

        if (!file.exists()) throw FileSystemException.InvalidPath(locator)
        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            file.inputStream()
        }
    }

    override suspend fun writeFile(
        locator: String,
        offset: Long,
        bytes: ByteArray,
        length: Int
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteFile(locator: String): Boolean {
        val file = File(locator)

        if (!file.exists()) return true
        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            file.delete()
        }
    }
}
