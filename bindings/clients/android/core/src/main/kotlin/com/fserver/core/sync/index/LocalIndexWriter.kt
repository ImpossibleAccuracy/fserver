package com.fserver.core.sync.index

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.IdGenerator
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.util.TimeProvider
import com.fserver.files.upload.FileRecord
import timber.log.Timber
import kotlin.time.Instant

/**
 * Every write to the local index outside a scan, under the same [SourceIndexLocks] the scan holds.
 * The bytes write describes must already be where it says.
 */
internal class LocalIndexWriter(
    private val storage: FServerStorage,
    private val timeProvider: TimeProvider,
    private val locks: SourceIndexLocks,
    private val versions: LocalVersions,
) {
    /** Records [hash], read from the bytes [local] described. */
    suspend fun recordHash(source: SourceEntry, local: FileRecord, hash: ContentHash) = recordHash(
        source = source,
        key = IndexedFileKey(fileId = local.id.value, sourceId = source.id),
        size = local.metadata.size,
        modifiedAt = local.metadata.lastModified,
        hash = hash,
    )

    /**
     * The one place a hash lands. Bytes that differ from the last hashed ones are an edit nobody
     * versioned yet, so they get a version now.
     */
    suspend fun recordHash(
        source: SourceEntry,
        key: IndexedFileKey,
        size: Long,
        modifiedAt: Instant,
        hash: ContentHash,
    ) = locks.withLock(source.id) {
        val row = storage.index.findFile(key) ?: return@withLock

        // Rescanned while hashing: the hash may describe bytes that are no longer there.
        if (row.state !is LocalIndexedFile.State.Present ||
            row.size.bytes != size ||
            row.modifiedAt != modifiedAt
        ) {
            Timber.d("Dropping hash of ${row.path} in source ${source.id}: file changed meanwhile")
            return@withLock
        }

        val edited = row.hash != null && row.hash != hash
        val version = if (edited) versions.issuer(1).after(row.version) else row.version

        storage.index.markProcessed(
            listOf(row.copy(hash = hash, hashStale = false, version = version))
        )
    }

    /**
     * Records a deletion done on the peer's behalf, as the peer's [version] - or, with none given,
     * as a new version of this device's own.
     */
    suspend fun recordDeleted(
        source: SourceEntry,
        key: IndexedFileKey,
        version: LocalIndexedFile.Version?,
    ) = locks.withLock(source.id) {
        val row = storage.index.findFile(key)
            ?: throw IllegalArgumentException("File ${key.fileId} not found in source ${source.id}")
        val now = timeProvider.now()

        storage.index.markProcessed(
            listOf(
                row.copy(
                    state = LocalIndexedFile.State.Deleted(deletedAt = now),
                    version = version ?: versions.issuer(1).after(row.version),
                    processedAt = now,
                )
            )
        )
    }

    /** Local bytes freed, the file kept in the set. Not a version: the peer's copy is the same file. */
    suspend fun recordEvicted(source: SourceEntry, key: IndexedFileKey) = locks.withLock(source.id) {
        storage.index.updateFileState(key, LocalIndexedFile.State.Evicted(evictedAt = timeProvider.now()))
    }

    /**
     * Records a rename done for a plan: the bytes of [from] now sit at [path] / [locator] as
     * [fileId] under [version], and [from] is deleted under [deletedVersion] - or a new version of
     * our own when null.
     */
    suspend fun recordMoved(
        source: SourceEntry,
        from: IndexedFileKey,
        deletedVersion: LocalIndexedFile.Version?,
        fileId: String,
        path: String,
        locator: String,
        modifiedAt: Instant,
        version: LocalIndexedFile.Version?,
    ) = locks.withLock(source.id) {
        val row = storage.index.findFile(from)
            ?: throw IllegalArgumentException("File ${from.fileId} not found in source ${source.id}")
        val existing = storage.index.findFile(IndexedFileKey(fileId = fileId, sourceId = source.id))
        val now = timeProvider.now()

        storage.index.markProcessed(
            listOf(
                row.copy(
                    state = LocalIndexedFile.State.Deleted(deletedAt = now),
                    version = deletedVersion ?: versions.issuer(1).after(row.version),
                    processedAt = now,
                ),
                // Same bytes, so pin, size and hash carry over.
                row.copy(
                    id = existing?.id ?: IdGenerator.nextId,
                    fileId = fileId,
                    path = path,
                    locator = locator,
                    modifiedAt = modifiedAt,
                    version = version,
                    processedAt = now,
                ),
            )
        )
    }

    /**
     * Records [file] as the peer pushed it, now placed at [locator]. [file] carries the verified
     * hash, and [modifiedAt] is what the disk reports, or the next scan reads a local edit.
     */
    suspend fun recordReceived(
        source: SourceEntry,
        file: FileRecord,
        locator: String,
        modifiedAt: Instant,
    ) = locks.withLock(source.id) {
        val saved = storage.index.findFile(IndexedFileKey(fileId = file.id.value, sourceId = source.id))

        val indexed = file
            .toIndexed(
                id = saved?.id ?: IdGenerator.nextId,
                sourceId = source.id,
                locator = locator,
                currentTime = timeProvider.now(),
            )
            // Pin and fetch time are this device's own: a new version keeps them.
            .let { it.copy(modifiedAt = modifiedAt, state = saved?.state as? LocalIndexedFile.State.Present ?: it.state) }

        storage.index.markProcessed(listOf(indexed))
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
    ) = locks.withLock(source.id) {
        val row = storage.index.findFile(key)
            ?: throw IllegalArgumentException("File ${key.fileId} not found in source ${source.id}")

        val unchanged =
            if (expected == null) row.isDeleted
            else !row.isDeleted && !row.hashStale && row.hash == expected
        check(unchanged) { "File ${row.path} in source ${source.id} changed since the merge was planned" }

        storage.index.markProcessed(
            listOf(row.copy(version = version))
        )
    }
}
