package com.fserver.files.fs.impl.local

import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.task.ProgressTask
import com.fserver.common.utils.SourcePaths
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FileSystemSource
import com.fserver.files.fs.FoundFile
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.ScanProgress
import com.fserver.files.fs.impl.isUnder
import com.fserver.files.fs.impl.scanTask
import com.fserver.files.fs.impl.segmentsOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Instant

internal class RootFileSystem(
    private val source: FileSystemSource.Root,
) : FileSystem {

    override fun scan(): ProgressTask<ScanProgress, List<FoundFile>> = scanTask(::scanFiles)

    override suspend fun createFile(path: String): FsFile =
        open(createLocalFile(resolve(path), path))

    override suspend fun checkPath(path: String) {
        resolve(path)
    }

    override suspend fun place(file: FsFile, path: String): FsFile =
        placeLocal(file, resolve(path), ::open)

    override suspend fun fileExists(path: String): Boolean {
        val file = resolve(path)

        return withContext(Dispatchers.IO) { file.isFile }
    }

    override suspend fun openFile(locator: String): FsFile? =
        existingLocalFile(confine(locator), locator)?.let(::open)

    private fun open(file: File): FsFile = LocalFile(file, ::confine)

    private suspend fun scanFiles(
        onFileFound: (FoundFile) -> Unit,
    ) = coroutineScope {
        for (volume in source.volumes) {
            launch { scanVolume(volume, onFileFound) }
        }
    }

    private suspend fun scanVolume(
        volume: FileSystemSource.Root.Volume,
        onFileFound: (FoundFile) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val root = File(volume.path)

        if (!root.exists()) throw FileSystemException.NotDirectory(volume.path)
        if (!root.isDirectory) throw FileSystemException.NotDirectory(volume.path)

        for (item in root.walkTopDown()) {
            currentCoroutineContext().ensureActive()

            if (!item.isFile) continue

            onFileFound(
                FoundFile(
                    path = SourcePaths.canonical(
                        volume = volume.id,
                        path = item.relativeTo(root).invariantSeparatorsPath,
                    ),
                    locator = item.absolutePath,
                    size = FileSize(item.length()),
                    lastModified = Instant.fromEpochMilliseconds(item.lastModified()),
                )
            )
        }
    }

    /**
     * [path] is volume-led, the way a scan here reports it: the first segment names the volume the
     * rest of the path lives on.
     */
    private fun resolve(path: String): File {
        val segments = segmentsOf(path)

        val mount = source.volumes
            .firstOrNull { it.id == segments.first() }
            ?.let(::mountOf)
            ?: throw FileSystemException.InvalidPath(path)

        // The volume itself is a directory, not a file the peer may create.
        if (segments.size < 2) throw FileSystemException.InvalidPath(path)

        // Canonical before the check: a symlink inside the volume still points wherever it points.
        return File(mount, segments.drop(1).joinToString("/")).canonicalFile.also {
            if (!it.isUnder(mount)) throw FileSystemException.InvalidPath(path)
        }
    }

    /**
     * A root source has no single directory to be confined to, so the mounted volumes are the whole
     * bound: a locator that resolves outside every one of them reaches nothing.
     */
    private fun confine(locator: String): File =
        File(locator).canonicalFile.also { file ->
            if (source.volumes.none { file.isUnder(mountOf(it)) }) {
                throw FileSystemException.InvalidPath(locator)
            }
        }

    private fun mountOf(volume: FileSystemSource.Root.Volume): File =
        File(volume.path).canonicalFile
}
