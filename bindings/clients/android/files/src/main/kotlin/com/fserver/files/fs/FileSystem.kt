package com.fserver.files.fs

import com.fserver.common.task.ProgressTask

/**
 * One [FileSystemSource] opened for work. The source is bound here, so callers pass paths and
 * locators only and never learn which backend serves them.
 *
 * Get one from [FileSystemEntryPoint.open].
 */
interface FileSystem {
    /** Walk the source, reporting files as they turn up. */
    fun scan(): ProgressTask<ScanProgress, List<FoundFile>>

    /**
     * Create a file at [path]. A media source refuses a name that is not image, video or audio
     * with [com.fserver.common.exception.FileSystemException.InvalidPath].
     */
    suspend fun createFile(path: String): FsFile

    /** True when a file exists at [path], same shape as [createFile] takes - and refuses. */
    suspend fun fileExists(path: String): Boolean

    /**
     * The file at [locator], or null when there is none.
     *
     * @throws com.fserver.common.exception.FileSystemException.InvalidPath when [locator] is outside
     *   this source, or names something that is not a file.
     */
    suspend fun openFile(locator: String): FsFile?
}
