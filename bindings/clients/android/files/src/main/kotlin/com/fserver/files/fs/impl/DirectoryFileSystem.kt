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
import java.io.RandomAccessFile
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
                    locator = item.locator(),
                    size = FileSize(item.length()),
                    lastModified = Instant.fromEpochMilliseconds(item.lastModified()),
                )
            )
        }
    }

    override suspend fun createFile(path: String): String {
        // Canonical before the check: `File(root, "../x").path` still starts with root.
        val file = File(root, path).canonicalFile
        ensureFileInRoot(file, path)

        if (file.exists()) throw FileSystemException.AlreadyExists(path)

        return withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            if (!file.createNewFile()) throw FileSystemException.CreationFailed(path)

            return@withContext file.locator()
        }
    }

    override suspend fun openFile(locator: String): InputStream {
        val file = confined(locator)

        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            file.inputStream()
        }
    }

    override suspend fun writeFile(
        locator: String,
        offset: Long,
        bytes: ByteArray,
        length: Int,
    ): Boolean {
        val file = confined(locator)

        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            RandomAccessFile(file, "rw").use { ra ->
                ra.seek(offset)
                ra.write(bytes, 0, length)
            }

            true
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
    private fun confined(locator: String): File =
        File(locator).canonicalFile.also {
            ensureFileInRoot(it, locator)
        }

    /** Ensure that [file] is under [root], throwing if not. */
    private fun ensureFileInRoot(file: File, locator: String) {
        if (!file.path.startsWith(root.canonicalFile.path + File.separator)) {
            throw FileSystemException.InvalidPath(locator)
        }
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

/** Locator for a file in the local filesystem. */
private fun File.locator(): String = this.absolutePath
