package com.fserver.core.sync.index

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.IdGenerator
import com.fserver.common.utils.SourcePaths
import com.fserver.core.files.ensureSourceReachable
import com.fserver.core.files.scan.toFiles
import com.fserver.core.files.util.FileHasher
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.core.sync.version.VersionVector
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.fs.FoundFile
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

internal class LocalChangesIndexer(
    private val store: FServerStorage,
    private val node: FilesNode,
    private val requirementsChecker: RequirementsChecker,
    private val timeProvider: TimeProvider,
    private val clock: HybridLogicalClock,
) {
    private val sourceLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Re-scan [source] and bring the index in line with what is on disk.
     *
     * Serialized per source: a local pass and a peer's index request both land here, and two
     * scans writing the same rows interleave into double-bumped version vectors.
     */
    suspend fun refresh(source: SourceEntry): List<LocalIndexedFile> =
        lockFor(source).withLock { runRefresh(source) }

    private suspend fun runRefresh(source: SourceEntry): List<LocalIndexedFile> {
        requirementsChecker.ensureSourceReachable(source.location)

        val currentTime = timeProvider.now()

        val savedState = store.index.processedFiles(source.id)
        val savedByPath = savedState
            .associateBy { it.path }
            .toMutableMap()

        // TODO: filter out temp files
        val actualState = node.openSource(source.location.toFiles())
            .scan()
            .result().getOrThrow()

        val new = mutableListOf<FoundFile>()
        val changed = mutableMapOf<LocalIndexedFile, FoundFile>()
        val touched = mutableMapOf<LocalIndexedFile, FoundFile>()

        for (file in actualState) {
            val saved = savedByPath.remove(file.path)
            when {
                saved == null -> new += file

                // File was deleted or evicted, and now is back
                saved.state !is LocalIndexedFile.State.Present -> changed[saved] = file

                file.size != saved.size -> changed[saved] = file

                // Same size, new mtime: only a hash can tell an edit from a touch, so the version
                // waits for it - unless there is no earlier hash to compare with.
                file.lastModified != saved.modifiedAt ->
                    if (saved.hash == null) changed[saved] = file else touched[saved] = file
            }
        }

        val toDelete = savedByPath.values.filter {
            it.state is LocalIndexedFile.State.Present
        }

        // Every certain change is a new version: a new file, new bytes, or a deletion.
        val versions = versionIssuer(new.size + changed.size + toDelete.size)

        val toSave =
            ArrayList<LocalIndexedFile>(new.size + changed.size + touched.size + toDelete.size).apply {
                for (file in new) {
                    this += file.toIndexed(
                        id = IdGenerator.nextId,
                        sourceId = source.id,
                        fileId = SourcePaths.fileId(file.path),
                        state = LocalIndexedFile.State.Present(
                            pinned = false,
                        ),
                        version = versions.after(null),
                        hash = null,
                        currentTime = currentTime,
                    )
                }

                for ((saved, file) in changed) {
                    this += file.toIndexed(
                        id = saved.id,
                        sourceId = source.id,
                        fileId = saved.fileId,
                        state =
                            // Restore file if it was deleted and now is back
                            saved.state as? LocalIndexedFile.State.Present
                                ?: LocalIndexedFile.State.Present(
                                    pinned = false,
                                ),
                        version = versions.after(saved.version),
                        hash = null,
                        currentTime = currentTime,
                    )
                }

                for ((saved, file) in touched) {
                    this += file.toIndexed(
                        id = saved.id,
                        sourceId = source.id,
                        fileId = saved.fileId,
                        state = saved.state,
                        version = saved.version,
                        hash = saved.hash,
                        hashStale = true,
                        currentTime = currentTime,
                    )
                }

                // A deletion is a version like any other, so it can be ordered against a remote edit.
                for (saved in toDelete) {
                    this += saved.copy(
                        state = LocalIndexedFile.State.Deleted(deletedAt = currentTime),
                        version = versions.after(saved.version),
                        hashStale = false,
                        processedAt = currentTime,
                    )
                }
            }

        store.index.markProcessed(toSave)

        // Return full state after all writes
        return store.index.processedFiles(source.id)
    }

    /** Run hash computation for file record */
    suspend fun hashFile(source: SourceEntry, local: FileRecord) {
        val locator = local.locator
            ?: error("Cannot hash file without locator: ${local.path} in source ${source.id}")

        val key = IndexedFileKey(fileId = local.id.value, sourceId = source.id)
        hashFile(
            source = source,
            key = key,
            locator = locator,
            size = local.metadata.size,
            modifiedAt = local.metadata.lastModified
        )
    }

    /** Run hash computation for indexed file */
    suspend fun hashFile(source: SourceEntry, local: LocalIndexedFile) {
        val key = IndexedFileKey(fileId = local.fileId, sourceId = source.id)
        hashFile(
            source = source,
            key = key,
            locator = local.locator,
            size = local.size.bytes,
            modifiedAt = local.modifiedAt
        )

        Timber.d("Hashed file ${local.path} in source ${source.id} with locator ${local.locator}")
    }

    /** Records [hash], read from the bytes [local] described while they were being sent. */
    suspend fun recordHash(source: SourceEntry, local: FileRecord, hash: ContentHash) {
        val key = IndexedFileKey(fileId = local.id.value, sourceId = source.id)
        recordHash(
            source = source,
            key = key,
            size = local.metadata.size,
            modifiedAt = local.metadata.lastModified,
            hash = hash
        )
    }

    /** Hashing reads the whole file, so it runs outside the source lock; [recordHash] re-checks. */
    private suspend fun hashFile(
        source: SourceEntry,
        key: IndexedFileKey,
        locator: String,
        size: Long,
        modifiedAt: Instant,
    ) {
        val hasher = FileHasher()

        withContext(Dispatchers.IO) {
            val fs = node.openSource(source.location.toFiles())
            fs.openFile(locator).use { stream ->
                val buffer = ByteArray(HashChunkSize)
                var bytesRead: Int

                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    hasher.write(buffer, bytesRead)
                }
            }
        }

        recordHash(
            source = source,
            key = key,
            size = size,
            modifiedAt = modifiedAt,
            hash = hasher.compute()
        )
    }

    /**
     * The one place a hash lands. Bytes that differ from the last hashed ones are an edit nobody
     * versioned yet, so they get a version now.
     */
    private suspend fun recordHash(
        source: SourceEntry,
        key: IndexedFileKey,
        size: Long,
        modifiedAt: Instant,
        hash: ContentHash,
    ) = lockFor(source).withLock {
        val row = store.index.findFile(key) ?: return@withLock

        // Rescanned while hashing: the hash may describe bytes that are no longer there.
        if (row.state !is LocalIndexedFile.State.Present ||
            row.size.bytes != size ||
            row.modifiedAt != modifiedAt
        ) {
            Timber.d("Dropping hash of ${row.path} in source ${source.id}: file changed meanwhile")
            return@withLock
        }

        val edited = row.hash != null && row.hash != hash
        val version = if (edited) versionIssuer(1).after(row.version) else row.version

        store.index.markProcessed(
            listOf(row.copy(hash = hash, hashStale = false, version = version))
        )
    }

    /**
     * Records a deletion done on the peer's behalf, as the peer's [version] - or, with none given,
     * as a new version of this device's own. The bytes must already be gone.
     */
    suspend fun recordDeleted(
        source: SourceEntry,
        key: IndexedFileKey,
        version: LocalIndexedFile.Version?
    ) =
        lockFor(source).withLock {
            val row = store.index.findFile(key)
                ?: throw IllegalArgumentException("File ${key.fileId} not found in source ${source.id}")
            val now = timeProvider.now()

            store.index.markProcessed(
                listOf(
                    row.copy(
                        state = LocalIndexedFile.State.Deleted(deletedAt = now),
                        version = version ?: versionIssuer(1).after(row.version),
                        hashStale = false,
                        processedAt = now,
                    )
                )
            )
        }

    /**
     * Records [version] for content both sides already agree on: [expected] bytes, or a deletion
     * when null. Refused when the file changed since, so a fresh local edit is never relabelled
     * as something the peer already has.
     */
    suspend fun adoptVersion(
        source: SourceEntry,
        key: IndexedFileKey,
        version: LocalIndexedFile.Version,
        expected: ContentHash?,
    ) = lockFor(source).withLock {
        val row = store.index.findFile(key)
            ?: throw IllegalArgumentException("File ${key.fileId} not found in source ${source.id}")

        val unchanged =
            if (expected == null) row.isDeleted
            else !row.isDeleted && !row.hashStale && row.hash == expected
        check(unchanged) { "File ${row.path} in source ${source.id} changed since the merge was planned" }

        store.index.markProcessed(
            listOf(row.copy(version = version))
        )
    }


    /** Issues [count] new versions of this device's own, each stamped with its own HLC reading. */
    private suspend fun versionIssuer(count: Int) = VersionIssuer(
        deviceId = store.identity.localDevice().deviceId,
        stamps = clock.ticks(count).iterator(),
    )

    private fun lockFor(source: SourceEntry): Mutex =
        sourceLocks.computeIfAbsent(source.id) { Mutex() }

    companion object {
        private const val HashChunkSize = 8192 // 8 KB chunk size
    }
}

private class VersionIssuer(
    private val deviceId: String,
    private val stamps: Iterator<HlcTimestamp>
) {
    fun after(previous: LocalIndexedFile.Version?) = LocalIndexedFile.Version(
        vector = (previous?.vector ?: VersionVector.Empty).bump(deviceId),
        hlc = stamps.next(),
        originDevice = deviceId,
    )
}

private fun FoundFile.toIndexed(
    id: String,
    sourceId: String,
    fileId: String,
    state: LocalIndexedFile.State,
    version: LocalIndexedFile.Version?,
    hash: ContentHash?,
    hashStale: Boolean = false,
    currentTime: Instant,
) = LocalIndexedFile(
    id = id,
    sourceId = sourceId,
    fileId = fileId,
    path = path,
    locator = locator,
    state = state,
    size = size,
    modifiedAt = lastModified,
    hash = hash,
    hashStale = hashStale,
    version = version,
    processedAt = currentTime,
)
