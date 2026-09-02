package com.fserver.files.fs

import com.fserver.common.task.ProgressTask
import java.io.InputStream

/**
 * One [FileSystemSource] opened for work. The source is bound here, so callers pass locators only and
 * never learn which backend serves them.
 *
 * Get one from [FileSystemEntryPoint.open].
 */
interface FileSystem {
    /** Walk the source, reporting files as they turn up. */
    fun scan(): ProgressTask<ScanProgress, List<FoundFile>>

    /** Open the file at [locator] for reading. */
    suspend fun openFile(locator: String): InputStream

    suspend fun deleteFile(locator: String): Boolean
}
