package com.fserver.files

import android.content.Context
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.scan.FoundFile
import com.fserver.files.fs.FileSystemSource
import com.fserver.files.fs.FileSystemEntryPoint
import com.fserver.files.fs.impl.local.DirectoryFileSystem
import java.io.File

/**
 * Entry point to `:files`. Build one per process and keep it.
 *
 * This is the module's whole runtime surface: everything else is `internal`, so a consumer picks
 * a [FileSystemSource] and gets [FoundFile]s back, and never learns whether that came from the
 * filesystem, the Storage Access Framework, or MediaStore.
 */
class FilesNode private constructor(
    private val context: Context,
    private val stagingDir: File?,
) {
    private val fileSystem: FileSystemEntryPoint by lazy { FileSystemEntryPoint(context) }

    private val staging: FileSystem by lazy { DirectoryFileSystem.staging(stagingRoot) }

    /** Where [openStaging] keeps its bytes. For measuring; write through [openStaging]. */
    val stagingRoot: File
        get() = stagingDir ?: File(context.cacheDir, StagingDirectory)

    /** Where every [FileSystemSource.Internal] bucket lives. For measuring; write through a source. */
    val internalRoot: File
        get() = DirectoryFileSystem.internalRoot(context)

    fun openSource(source: FileSystemSource): FileSystem = fileSystem.open(source)

    /**
     * App-private scratch space for bytes on their way into a source. It is cache: the system may
     * clear it under storage pressure, so nothing here may be the only copy of anything.
     */
    fun openStaging(): FileSystem = staging

    companion object {
        private const val StagingDirectory = "downloads"

        /** @param stagingDir overrides `cacheDir/downloads` for [openStaging]. */
        fun create(context: Context, stagingDir: File? = null): FilesNode = FilesNode(
            context = context,
            stagingDir = stagingDir,
        )
    }
}
