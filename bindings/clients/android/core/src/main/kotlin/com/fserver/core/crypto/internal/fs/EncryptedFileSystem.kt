package com.fserver.core.crypto.internal.fs

import com.fserver.common.exception.FileSystemException
import com.fserver.common.model.FileSize
import com.fserver.common.task.ProgressTask
import com.fserver.common.utils.IdGenerator
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.crypto.format.SealedHeader
import com.fserver.core.crypto.internal.SealedFiles
import com.fserver.core.crypto.internal.SealedFsFile
import com.fserver.core.crypto.model.AtRest
import com.fserver.core.crypto.model.EncryptionPolicy
import com.fserver.core.store.sync.FileIndexStore
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.impl.PartMarker
import com.fserver.files.fs.scan.FoundFile
import com.fserver.files.fs.scan.ScanProgress
import com.fserver.files.fs.scan.ScanTree
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * [inner] as plaintext: sealed files are opened through their header, and under
 * [EncryptionPolicy.Required] new ones are sealed as they land. Sizes are plaintext sizes.
 *
 * The index, not the header, decides what a plain file is (Storage Encryption §6.1): under
 * `Required`, plaintext where the index recorded a sealed file is refused, not read.
 */
internal class EncryptedFileSystem(
    private val inner: FileSystem,
    private val source: SourceEntry,
    private val files: SealedFiles,
    private val index: FileIndexStore,
) : SourceFileSystem {
    private val policy get() = source.preferences.encryption as? EncryptionPolicy.Required
    private val required get() = policy != null
    private val scanned = ConcurrentHashMap<String, AtRest>()

    override fun scan(): ProgressTask<ScanProgress, List<FoundFile>> =
        inner.scan().then { reconcile(it) }

    override fun scanTree(): ProgressTask<ScanProgress, ScanTree> =
        inner.scanTree().then { it.copy(files = reconcile(it.files)) }

    override fun atRestOf(locator: String): AtRest = scanned[locator] ?: AtRest.Plain

    override suspend fun openFile(locator: String): FsFile? {
        val raw = inner.openFile(locator) ?: return null
        val header = try {
            raw.openReader().use { files.headerOf(it) }
        } catch (e: FileSystemException.Corrupted) {
            // Plaintext that merely starts like a header, unless the index says otherwise.
            if (recorded(locator)?.atRest is AtRest.Sealed) throw e
            null
        }
        if (header != null) return SealedFsFile(raw, header, files)

        if (required && recorded(locator)?.atRest is AtRest.Sealed) {
            throw FileSystemException.Corrupted("plaintext at $locator, where a sealed file was")
        }
        return raw
    }

    override suspend fun fileExists(path: String): Boolean = inner.fileExists(path)

    override suspend fun checkPath(path: String) = inner.checkPath(path)

    override suspend fun createFile(path: String): FsFile {
        val policy = policy ?: return inner.createFile(path)

        val sealed = files.create(source.id, policy.cipherId)
        val raw = inner.createFile(path)
        try {
            raw.openWriter().use {
                sealed.initialize(it)
                it.sync()
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) { raw.delete() }
            throw e
        }
        return SealedFsFile(raw, sealed.header, files)
    }

    /**
     * A sealed [file] moves as it is. Plaintext under `Required` is sealed into a part file beside
     * [path] that then replaces it, so a failure leaves the old file and [file] untouched.
     */
    override suspend fun place(file: FsFile, path: String): FsFile {
        if (file is SealedFsFile) return SealedFsFile(
            inner.place(file.raw, path),
            file.header,
            files
        )
        val policy = policy ?: return inner.place(file, path)

        val sealed = files.create(source.id, policy.cipherId)
        val part = inner.createFile(partPathOf(path))
        try {
            file.read().use { input ->
                part.openWriter().use {
                    sealed.sealAll(input, it)
                    it.sync()
                }
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) { part.delete() }
            throw e
        }

        val placed = part.rename(path.substringAfterLast('/'), deleteOldOnConflict = true)
        file.delete()
        return SealedFsFile(placed, sealed.header, files)
    }

    /** [found] with plaintext sizes. A header is read only where the index cannot vouch for the file. */
    private suspend fun reconcile(found: List<FoundFile>): List<FoundFile> {
        val rows = index.processedFiles(source.id)
            .filter { it.state is LocalIndexedFile.State.Present }
            .associateBy { it.locator }

        return found.map { file ->
            // Never indexed, so its size does not matter and its header is not worth a read.
            if (PartMarker in file.path.substringAfterLast('/')) return@map file

            val row = rows[file.locator]
            val (reconciled, atRest) = known(file, row) ?: read(file, row)
            scanned[file.locator] = atRest
            reconciled
        }
    }

    /** What [row] says, when [file] is still the bytes it described. */
    private fun known(file: FoundFile, row: LocalIndexedFile?): Pair<FoundFile, AtRest>? {
        if (row == null || row.modifiedAt != file.lastModified) return null

        return when (val atRest = row.atRest) {
            AtRest.Plain -> if (row.size == file.size) file to atRest else null
            is AtRest.Sealed ->
                if (files.rawSizeOf(
                        row.size.bytes,
                        atRest
                    ) == file.size.bytes
                ) file.copy(size = row.size) to atRest else null
        }
    }

    private suspend fun read(file: FoundFile, row: LocalIndexedFile?): Pair<FoundFile, AtRest> {
        val header = runCatchingCancellable {
            inner.openFile(file.locator)?.openReader()?.use { reader ->
                files.headerOf(reader)?.let { it to it.layout.plainSize(reader.size()) }
            }
        }.getOrElse {
            Timber.w(it, "Unreadable header of ${file.path} in source ${source.id}")
            // Left as the index has it: anything else reads as an edit or a deletion to the peer.
            return if (row != null) unchanged(file, row) else file to AtRest.Plain
        }

        if (header != null) {
            val (sealed, size) = header
            return file.copy(size = FileSize(size)) to sealed.atRest
        }

        if (required && row?.atRest is AtRest.Sealed) {
            Timber.w("Plaintext replaced sealed ${file.path} in source ${source.id}: left as it was")
            return unchanged(file, row)
        }
        return file to AtRest.Plain
    }

    private fun unchanged(file: FoundFile, row: LocalIndexedFile) =
        file.copy(size = row.size, lastModified = row.modifiedAt) to row.atRest

    private suspend fun recorded(locator: String): LocalIndexedFile? =
        index.findByLocator(source.id, locator)
}

private val SealedHeader.atRest: AtRest.Sealed
    get() = AtRest.Sealed(cipherId = cipherId, keyId = keyId)

/**
 * Beside [path], named so a scan ignores it (see `IgnoredPaths`) and unique, since nothing can
 * delete a stale one by path - `GarbageCollector` sweeps those.
 */
private fun partPathOf(path: String): String {
    val directory = path.substringBeforeLast('/', "")
    val name = "${path.substringAfterLast('/')}.${IdGenerator.nextId}${PartMarker}"
    return if (directory.isEmpty()) name else "$directory/$name"
}

/** [this], then [transform] over its result - which, unlike `ProgressTask.map`, may suspend. */
private fun <P, R1, R2> ProgressTask<P, R1>.then(transform: suspend (R1) -> R2): ProgressTask<P, R2> {
    val delegate = this
    return object : ProgressTask<P, R2> {
        override val progress: Flow<P> get() = delegate.progress

        override suspend fun result(): Result<R2> =
            delegate.result()
                .fold({ runCatchingCancellable { transform(it) } }, { Result.failure(it) })
    }
}
