package com.fserver.files.fs

import com.fserver.common.task.ProgressTask
import java.io.InputStream
import kotlin.time.Instant

/**
 * One [FileSystemSource] opened for work. The source is bound here, so callers pass locators only and
 * never learn which backend serves them.
 *
 * Get one from [FileSystemEntryPoint.open].
 */
interface FileSystem {
    /** Walk the source, reporting files as they turn up. */
    fun scan(): ProgressTask<ScanProgress, List<FoundFile>>

    /** Create a file at [path]. */
    suspend fun createFile(path: String): String

    /** True when a file exists at [path], same shape as [createFile] takes. */
    suspend fun fileExists(path: String): Boolean

    /**
     * Rename the file at [locator] to [newName] - a name, not a path - within its directory.
     * With [deleteOldOnConflict], a file already named [newName] is replaced; otherwise it is a refusal.
     *
     * @return new locator for the file
     * @throws com.fserver.common.exception.FileSystemException.RenameRejected when the rename did
     *   not happen. The file then stays at [locator], and a file it was to replace stays too.
     */
    suspend fun renameFile(
        locator: String,
        newName: String,
        deleteOldOnConflict: Boolean = false,
    ): String

    /** Open the file at [locator] for reading. */
    suspend fun openFile(locator: String): InputStream

    /** Write [bytes] to the file at [locator], starting at [offset]. */
    suspend fun writeFile(
        locator: String,
        offset: Long,
        bytes: ByteArray,
        length: Int = bytes.size,
    ): Boolean

    suspend fun deleteFile(locator: String): Boolean

    /**
     * Sets the file's mtime to [time] where the backend allows it, then returns the mtime a scan
     * will report for it - which is not [time] when the backend refused or rounded it.
     */
    suspend fun settleLastModified(locator: String, time: Instant): Instant
}
