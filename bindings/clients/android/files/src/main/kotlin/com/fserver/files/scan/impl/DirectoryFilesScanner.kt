package com.fserver.files.scan.impl

import com.fserver.files.model.FileSystemException
import com.fserver.files.scan.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

internal object DirectoryFilesScanner {
    suspend fun scanDirectory(
        directoryPath: String,
        onFileFound: (ScannedFile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val file = File(directoryPath)

        if (!file.exists()) throw FileSystemException.NotDirectory(directoryPath)
        if (!file.isDirectory) throw FileSystemException.NotDirectory(directoryPath)

        for (item in file.walkTopDown()) {
            currentCoroutineContext().ensureActive()

            if (!item.isFile) continue

            onFileFound(
                ScannedFile(
                    path = item.absolutePath,
                    directory = item.parent ?: directoryPath,
                    size = item.length()
                )
            )
        }
    }
}
