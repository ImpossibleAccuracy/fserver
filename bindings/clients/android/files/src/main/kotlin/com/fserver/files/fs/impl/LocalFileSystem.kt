package com.fserver.files.fs.impl

import com.fserver.common.exception.FileSystemException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Backends served by [File], where a locator is an absolute path.
 *
 * What a locator may reach is the whole security boundary here, and it is the only thing that
 * differs between these backends — so the operations live here and an impl only says which files
 * it owns.
 */
internal abstract class LocalFileSystem : SystemAdapter() {
    /** [path] — source-relative, from a peer — as the file it names. Throws if it escapes. */
    protected abstract fun resolve(path: String): File

    /** [locator] as the file it names. Throws if it resolves outside this source. */
    protected abstract fun confine(locator: String): File

    /**
     * Called once [file] has appeared or gone, for a source that keeps an index beside the disk.
     * Writes do not report: a chunk is not a change anything outside this source can see yet.
     */
    protected open fun onFileChanged(file: File) = Unit

    final override suspend fun createFile(path: String): String {
        val file = resolve(path)

        if (file.exists()) throw FileSystemException.AlreadyExists(path)

        return withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            if (!file.createNewFile()) throw FileSystemException.CreationFailed(path)

            onFileChanged(file)

            file.absolutePath
        }
    }

    final override suspend fun openFile(locator: String): InputStream {
        val file = confine(locator)

        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            file.inputStream()
        }
    }

    final override suspend fun writeFile(
        locator: String,
        offset: Long,
        bytes: ByteArray,
        length: Int,
    ): Boolean {
        val file = confine(locator)

        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            RandomAccessFile(file, "rw").use { ra ->
                ra.seek(offset)
                ra.write(bytes, 0, length)
            }

            true
        }
    }

    final override suspend fun deleteFile(locator: String): Boolean {
        val file = confine(locator)

        if (!file.exists()) return true
        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            file.delete().also { if (it) onFileChanged(file) }
        }
    }
}

/** True when this file sits under [root]. Both sides must already be canonical. */
internal fun File.isUnder(root: File): Boolean = path.startsWith(root.path + File.separator)
