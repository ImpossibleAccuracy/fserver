package com.fserver.files.fs.impl.local

import com.fserver.common.exception.FileSystemException
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.impl.nameOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.time.Instant

/** A file served by [File], where the locator is an absolute path. */
internal class LocalFile(
    private val file: File,
    /**
     * owning source's bound - what a locator may reach is the whole security boundary
     * for these backends, and a rename target must stay inside it too.
     */
    private val confine: (locator: String) -> File,
    /**
     * runs once a file has appeared or gone, for a source that keeps an index beside the disk;
     * write does not report, since a chunk is not a change anything outside this source can see yet.
     */
    private val onChanged: (File) -> Unit = {},
) : FsFile {
    override val locator: String = file.absolutePath

    override suspend fun read(): InputStream = withContext(Dispatchers.IO) {
        file.inputStream()
    }

    override suspend fun write(offset: Long, bytes: ByteArray, length: Int) {
        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        withContext(Dispatchers.IO) {
            RandomAccessFile(file, "rw").use { ra ->
                ra.seek(offset)
                ra.write(bytes, 0, length)
            }
        }
    }

    override suspend fun rename(newName: String, deleteOldOnConflict: Boolean): FsFile {
        val target = confine(File(file.parentFile, nameOf(newName)).path)

        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            if (target.exists() && !(deleteOldOnConflict && target.isFile)) {
                throw FileSystemException.RenameRejected(locator, newName)
            }

            // rename(2) replaces the target in one step, so there is no moment with neither file.
            if (!file.renameTo(target)) throw FileSystemException.RenameRejected(locator, newName)

            onChanged(file)
            onChanged(target)

            LocalFile(target, confine, onChanged)
        }
    }

    override suspend fun delete(): Boolean {
        if (!file.exists()) return true
        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            file.delete().also { if (it) onChanged(file) }
        }
    }

    override suspend fun settleLastModified(time: Instant): Instant {
        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) {
            // Best effort: some mounts refuse it, and FAT keeps 2 s precision.
            file.setLastModified(time.toEpochMilliseconds())
            Instant.fromEpochMilliseconds(file.lastModified())
        }
    }
}

/** Creates [file], already resolved and confined from the peer's [path], with its directories. */
internal suspend fun createLocalFile(
    file: File,
    path: String,
    onChanged: (File) -> Unit = {},
): File {
    if (file.exists()) throw FileSystemException.AlreadyExists(path)

    return withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        if (!file.createNewFile()) throw FileSystemException.CreationFailed(path)

        onChanged(file)

        file
    }
}

/** [file] when a file is there, null when nothing is. Throws when it is a directory. */
internal suspend fun existingLocalFile(file: File, locator: String): File? =
    withContext(Dispatchers.IO) {
        when {
            !file.exists() -> null
            file.isFile -> file
            else -> throw FileSystemException.InvalidPath(locator)
        }
    }
