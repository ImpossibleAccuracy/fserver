package com.fserver.core.support

import com.fserver.common.exception.FileSystemException
import com.fserver.common.task.ProgressTask
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.ScanProgress
import java.io.ByteArrayInputStream
import java.io.InputStream

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

    /** Set to have [createFile] fail, the way a backend refuses a path outside its root. */
    var rejectCreate: ((String) -> Throwable?)? = null

    fun bytesAt(locator: String): ByteArray? = files[locator]

    override fun scan(): ProgressTask<ScanProgress, List<FoundFile>> =
        throw UnsupportedOperationException("scan is not part of the upload path")

    override suspend fun createFile(path: String): String {
        rejectCreate?.invoke(path)?.let { throw it }

        if (files.containsKey(path)) throw FileSystemException.AlreadyExists(path)

        createdPaths += path
        files[path] = ByteArray(0)
        return path
    }

    override suspend fun openFile(locator: String): InputStream =
        ByteArrayInputStream(files[locator] ?: throw FileSystemException.InvalidPath(locator))

    override suspend fun writeFile(
        locator: String,
        offset: Long,
        bytes: ByteArray,
        length: Int,
    ): Boolean {
        val current = files[locator] ?: throw FileSystemException.InvalidPath(locator)
        val end = (offset + length).toInt()

        val grown = if (current.size < end) current.copyOf(end) else current
        bytes.copyInto(grown, destinationOffset = offset.toInt(), startIndex = 0, endIndex = length)
        files[locator] = grown

        return true
    }

    override suspend fun deleteFile(locator: String): Boolean {
        deleted += locator
        return files.remove(locator) != null
    }
}
