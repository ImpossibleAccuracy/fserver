package com.fserver.files.fs

import com.fserver.common.exception.FileSystemException
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

    /** True when a file exists at [path], same shape as [createFile] takes - and refuses. */
    suspend fun fileExists(path: String): Boolean

    /**
     * The file at [locator], or null when there is none.
     *
     * @throws FileSystemException.InvalidPath when [locator] is outside this source, or names something that is not a file.
     */
    suspend fun openFile(locator: String): FsFile?

    /**
     * Create a file at [path].
     * Some source may refuse names with [FileSystemException.InvalidPath].
     */
    suspend fun createFile(path: String): FsFile

    /** Throws [FileSystemException.InvalidPath] where [createFile] would. */
    suspend fun checkPath(path: String)

    /**
     * Puts [file] - from any [FileSystem] - at [path], replacing whatever is there.
     * Rename where the backends allow it, a copy otherwise; [file] is gone after.
     *
     * @return the file now at [path]
     */
    suspend fun place(file: FsFile, path: String): FsFile
}
