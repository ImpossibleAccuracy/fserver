package com.fserver.files.fs.impl

import android.content.Context
import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FoundFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.time.Instant

/**
 * One directory on the local filesystem, walked as a whole.
 *
 * Paths are reported relative to [root] with no volume leading them, which is what a source scoped
 * to one directory needs: a device hosting a peer's source lays the peer's canonical paths out
 * underneath [root], so the two sides agree on a path without agreeing on where it lives.
 */
internal class DirectoryFileSystem(
    private val root: File,
) : SystemAdapter() {

    override suspend fun scanFiles(
        onFileFound: (FoundFile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // Nothing hosted here yet: the directory is created by the first file that arrives.
        if (!root.exists()) return@withContext
        if (!root.isDirectory) throw FileSystemException.NotDirectory(root.absolutePath)

        for (item in root.walkTopDown()) {
            currentCoroutineContext().ensureActive()

            if (!item.isFile) continue

            onFileFound(
                FoundFile(
                    path = SourcePaths.canonical(
                        volume = null,
                        path = item.relativeTo(root).invariantSeparatorsPath,
                    ),
                    locator = item.absolutePath,
                    size = FileSize(item.length()),
                    lastModified = Instant.fromEpochMilliseconds(item.lastModified()),
                )
            )
        }
    }

    override suspend fun openFile(locator: String): InputStream {
        val file = confined(locator)

        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            file.inputStream()
        }
    }

    override suspend fun deleteFile(locator: String): Boolean {
        val file = confined(locator)

        if (!file.exists()) return true
        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            file.delete()
        }
    }

    /**
     * [locator] resolved under [root]. Checked rather than trusted: every byte in this directory
     * arrived from a peer, so a locator that walks back out of it must not open anything.
     */
    private fun confined(locator: String): File {
        val file = File(locator).canonicalFile
        val base = root.canonicalFile

        if (!file.path.startsWith(base.path + File.separator)) {
            throw FileSystemException.InvalidPath(locator)
        }

        return file
    }

    companion object {
        private const val SourcesDirectory = "sources"

        /**
         * App-private storage, one directory per bucket.
         *
         * Nothing outside the app can reach what lands here, no runtime permission gates it, and
         * it goes with an uninstall.
         */
        fun internal(context: Context, bucket: String): DirectoryFileSystem =
            DirectoryFileSystem(File(File(context.filesDir, SourcesDirectory), bucket))
    }
}
