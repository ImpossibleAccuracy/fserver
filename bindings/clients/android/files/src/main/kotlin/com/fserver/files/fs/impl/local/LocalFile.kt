package com.fserver.files.fs.impl.local

import android.os.ParcelFileDescriptor
import com.fserver.common.exception.FileSystemException
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsReader
import com.fserver.files.fs.FsWriter
import com.fserver.files.fs.impl.StreamTarget
import com.fserver.files.fs.impl.ChannelReader
import com.fserver.files.fs.impl.ChannelWriter
import com.fserver.files.fs.impl.nameOf
import com.fserver.files.fs.impl.partNameOf
import com.fserver.files.fs.impl.placeByCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import kotlin.time.Instant

/** A file served by [File], where the locator is an absolute path. */
internal class LocalFile(
    internal val file: File,
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
) : FsFile, StreamTarget {
    override val locator: String = file.absolutePath

    override suspend fun read(): InputStream = withContext(Dispatchers.IO) {
        file.inputStream()
    }

    override suspend fun openReader(): FsReader {
        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) { ChannelReader(RandomAccessFile(file, "r").channel) }
    }

    override suspend fun openDescriptor(): ParcelFileDescriptor {
        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
    }

    override suspend fun openWriter(): FsWriter {
        if (!file.isFile) throw FileSystemException.InvalidPath(locator)

        return withContext(Dispatchers.IO) { ChannelWriter(RandomAccessFile(file, "rw").channel) }
    }

    /** Creates the file when it is missing: [placeLocal] copies into a part that is not there yet. */
    override suspend fun openOutput(): OutputStream =
        withContext(Dispatchers.IO) { FileOutputStream(file) }

    /** Tells the owning source this file was renamed away by [placeLocal]. */
    internal fun movedAway() = onChanged(file)

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

/**
 * [com.fserver.files.fs.FileSystem.place] for the local backends: rename(2) onto [target], which
 * replaces whatever is there in one step. Across mounts - app storage to `/storage/...` - that is
 * `EXDEV`, so the bytes are copied beside [target] and renamed over it instead.
 *
 * @param target already resolved and confined by the owning source
 * @param open wraps a file of the owning source; [onChanged] is what that source runs on a change
 */
internal suspend fun placeLocal(
    file: FsFile,
    target: File,
    open: (File) -> FsFile,
    onChanged: (File) -> Unit = {},
): FsFile {
    val renamed = withContext(Dispatchers.IO) {
        if (target.isDirectory) throw FileSystemException.InvalidPath(target.path)
        target.parentFile?.mkdirs()

        file is LocalFile && file.file.renameTo(target)
    }

    if (renamed) {
        (file as LocalFile).movedAway()
        onChanged(target)
        return open(target)
    }

    val part = File(target.parentFile, partNameOf(target.name))

    return placeByCopy(file, LocalFile(part, { File(it) })) {
        withContext(Dispatchers.IO) {
            if (!part.renameTo(target)) throw FileSystemException.RenameRejected(part.path, target.name)
        }

        onChanged(target)
        open(target)
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
