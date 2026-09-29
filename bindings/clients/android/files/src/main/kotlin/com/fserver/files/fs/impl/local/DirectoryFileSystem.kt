package com.fserver.files.fs.impl.local

import android.content.Context
import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.task.ProgressTask
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.scan.FoundDirectory
import com.fserver.files.fs.scan.FoundFile
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.scan.ScanProgress
import com.fserver.files.fs.scan.ScanTree
import com.fserver.files.fs.impl.isUnder
import com.fserver.files.fs.scan.scanTask
import com.fserver.files.fs.scan.treeScanTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Instant

/**
 * One directory on the local filesystem, walked as a whole.
 *
 * Paths are reported relative to [root] with no volume leading them, which is what a source scoped
 * to one directory needs: a device hosting a peer's source lays the peer's canonical paths out
 * underneath [root], so the two sides agree on a path without agreeing on where it lives.
 */
internal class DirectoryFileSystem(
    private val root: File,
    /** Removes the directories a deleted or moved file leaves empty, up to [root]. */
    private val pruneEmptyDirs: Boolean = false,
) : FileSystem {

    override fun scan(): ProgressTask<ScanProgress, List<FoundFile>> =
        scanTask { onFileFound -> scanFiles(onFileFound, onDirectoryFound = null) }

    override fun scanTree(): ProgressTask<ScanProgress, ScanTree> = treeScanTask(::scanFiles)

    override suspend fun createFile(path: String): FsFile =
        open(createLocalFile(resolve(path), path, ::onChanged))

    override suspend fun checkPath(path: String) {
        resolve(path)
    }

    override suspend fun place(file: FsFile, path: String): FsFile =
        placeLocal(file, resolve(path), ::open, ::onChanged)

    override suspend fun fileExists(path: String): Boolean {
        val file = resolve(path)

        return withContext(Dispatchers.IO) { file.isFile }
    }

    override suspend fun openFile(locator: String): FsFile? =
        existingLocalFile(confine(locator), locator)?.let(::open)

    private fun open(file: File): FsFile = LocalFile(file, ::confine, ::onChanged)

    private fun onChanged(file: File) {
        if (!pruneEmptyDirs || file.exists()) return

        // delete() refuses a directory that still holds anything, which ends the walk.
        var dir = file.parentFile
        while (dir != null && dir.isUnder(root.canonicalFile) && dir.delete()) {
            dir = dir.parentFile
        }
    }

    private suspend fun scanFiles(
        onFileFound: (FoundFile) -> Unit,
        onDirectoryFound: ((FoundDirectory) -> Unit)?,
    ) = withContext(Dispatchers.IO) {
        // Nothing hosted here yet: the directory is created by the first file that arrives.
        if (!root.exists()) return@withContext
        if (!root.isDirectory) throw FileSystemException.NotDirectory(root.absolutePath)

        for (item in root.walkTopDown()) {
            currentCoroutineContext().ensureActive()

            if (item.isDirectory && item != root) {
                onDirectoryFound?.invoke(
                    FoundDirectory(
                        path = SourcePaths.canonical(
                            volume = null,
                            path = item.relativeTo(root).invariantSeparatorsPath,
                        ),
                        locator = item.absolutePath,
                    )
                )
            }

            if (!item.isFile) continue

            onFileFound(
                FoundFile(
                    path = SourcePaths.canonical(
                        volume = null,
                        path = item.relativeTo(root).invariantSeparatorsPath,
                    ),
                    locator = item.absolutePath,
                    size = FileSize(item.length()),
                    lastModified = Instant.fromEpochMilliseconds(item.lastModified()),
                )
            )
        }
    }

    // Canonical before the check: `File(root, "../x").path` still starts with root.
    private fun resolve(path: String): File = ensureInRoot(File(root, path).canonicalFile, path)

    private fun confine(locator: String): File =
        ensureInRoot(File(locator).canonicalFile, locator)

    /**
     * Ensure that [file] is under [root], throwing if not. Checked rather than trusted: every byte
     * in this directory arrived from a peer, so a path that walks back out must not open anything.
     */
    private fun ensureInRoot(file: File, path: String): File = file.also {
        if (!it.isUnder(root.canonicalFile)) throw FileSystemException.InvalidPath(path)
    }

    companion object {
        private const val SourcesDirectory = "sources"

        /** Scratch space: every file is transient, so nothing keeps the directories it leaves. */
        fun staging(root: File): DirectoryFileSystem =
            DirectoryFileSystem(root, pruneEmptyDirs = true)

        /**
         * App-private storage, one directory per bucket.
         *
         * Nothing outside the app can reach what lands here, no runtime permission gates it, and
         * it goes with an uninstall.
         */
        fun internal(context: Context, bucket: String): DirectoryFileSystem =
            DirectoryFileSystem(File(internalRoot(context), bucket))

        /** The directory every [internal] bucket sits in. */
        fun internalRoot(context: Context): File = File(context.filesDir, SourcesDirectory)
    }
}
