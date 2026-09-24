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
