package com.fserver.core.support

import com.fserver.common.exception.FileSystemException
import com.fserver.common.task.ProgressTask
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsWriter
import com.fserver.files.fs.ScanProgress
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.time.Instant

/**
 * Bytes in a map, addressed by the path they were created under.
 *
 * Deliberately not confined: path confinement is the real backend's job and is tested against
 * `DirectoryFileSystem` in `:files`. This one exists so an upload can be driven without a disk.
 */
internal class InMemoryFileSystem : FileSystem {
    private val files = linkedMapOf<String, ByteArray>()

    val createdPaths: MutableList<String> = mutableListOf()
    val deleted: MutableList<String> = mutableListOf()
    val synced: MutableList<String> = mutableListOf()

    /** Set to have [createFile] fail, the way a backend refuses a path outside its root. */
    var rejectCreate: ((String) -> Throwable?)? = null

    fun bytesAt(locator: String): ByteArray? = files[locator]

    /** mtime per locator, as [FsFile.settleLastModified] left it. */
    val modified: MutableMap<String, Instant> = mutableMapOf()

    /** Set to model a backend that owns mtime and ignores the requested one. */
    var fixedModified: Instant? = null

    override fun scan(): ProgressTask<ScanProgress, List<FoundFile>> =
        throw UnsupportedOperationException("scan is not part of the upload path")

    override suspend fun createFile(path: String): FsFile {
        rejectCreate?.invoke(path)?.let { throw it }

        if (files.containsKey(path)) throw FileSystemException.AlreadyExists(path)

        createdPaths += path
        files[path] = ByteArray(0)
        return InMemoryFile(path)
    }

    override suspend fun checkPath(path: String) {
        rejectCreate?.invoke(path)?.let { throw it }
    }

    override suspend fun place(file: FsFile, path: String): FsFile {
        checkPath(path)

        files[path] = file.read().use { it.readBytes() }
        file.delete()
        return InMemoryFile(path)
    }

    override suspend fun fileExists(path: String): Boolean = path in files

    override suspend fun openFile(locator: String): FsFile? =
        if (locator in files) InMemoryFile(locator) else null

    private inner class InMemoryFile(override val locator: String) : FsFile {
        override suspend fun read(): InputStream =
            ByteArrayInputStream(files[locator] ?: throw FileSystemException.InvalidPath(locator))

        override suspend fun openWriter(): FsWriter {
            if (locator !in files) throw FileSystemException.InvalidPath(locator)

            return object : FsWriter {
                override suspend fun write(offset: Long, bytes: ByteArray, length: Int) {
                    val current = files[locator] ?: throw FileSystemException.InvalidPath(locator)
                    val end = (offset + length).toInt()

                    val grown = if (current.size < end) current.copyOf(end) else current
                    bytes.copyInto(grown, destinationOffset = offset.toInt(), startIndex = 0, endIndex = length)
                    files[locator] = grown
                }

                override suspend fun sync() {
                    synced += locator
                }

                override fun close() = Unit
            }
        }

        override suspend fun rename(newName: String, deleteOldOnConflict: Boolean): FsFile {
            val bytes = files[locator] ?: throw FileSystemException.InvalidPath(locator)
            val target = locator.substringBeforeLast('/', "").let { if (it.isEmpty()) newName else "$it/$newName" }

            if (target in files && !deleteOldOnConflict) throw FileSystemException.RenameRejected(locator, newName)

            files.remove(locator)
            files[target] = bytes
            return InMemoryFile(target)
        }

        override suspend fun delete(): Boolean {
            deleted += locator
            files.remove(locator)
            return true
        }

        override suspend fun settleLastModified(time: Instant): Instant {
            if (locator !in files) throw FileSystemException.InvalidPath(locator)
            return (fixedModified ?: time).also { modified[locator] = it }
        }
    }
}
