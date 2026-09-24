package com.fserver.files.fs

import java.io.InputStream
import kotlin.time.Instant

/** One file of an opened [FileSystem]; every call goes through the backend it came from. */
interface FsFile {
    /** Where the file lives, in the backend's own terms. Stable until [rename]. */
    val locator: String

    /** Open the file for reading. */
    suspend fun read(): InputStream

    /** Write [bytes] starting at [offset]. Throws if nothing was written. */
    suspend fun write(
        offset: Long,
        bytes: ByteArray,
        length: Int = bytes.size,
    )

    /**
     * Rename the file to [newName] - a name, not a path - within its directory.
     * With [deleteOldOnConflict], a file already named [newName] is replaced; otherwise it is a refusal.
     * A backend that cannot find the file in the way (SAF without `findDocumentPath`) refuses either way.
     * A media source refuses a non-media [newName] as [FileSystem.createFile] does.
     *
     * @return the file under its new name; this one is stale afterwards
     * @throws com.fserver.common.exception.FileSystemException.RenameRejected when the rename did
     *   not happen. The file then stays where it was, and a file it was to replace stays too.
     */
    suspend fun rename(
        newName: String,
        deleteOldOnConflict: Boolean = false,
    ): FsFile

    /**
     * True once the file is gone - also when it already was. False when the backend refused,
     * e.g. a provider that does not support delete or a MediaStore row another app owns.
     */
    suspend fun delete(): Boolean

    /**
     * Sets the mtime to [time] where the backend allows it, then returns the mtime a scan will
     * report - which is not [time] when the backend refused or rounded it.
     */
    suspend fun settleLastModified(time: Instant): Instant
}
