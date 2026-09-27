package com.fserver.core.sync.runner

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.ContentHashDto
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.network.utils.runRemoteOperation
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.conflict.ConflictCopies
import com.fserver.core.sync.conflict.ConflictDecision
import com.fserver.core.sync.conflict.seenVersion
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.toIndexed
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException

/**
 * Carries out one planned [FileAction] and nothing more.
 * Ordering, retries and what a failure means are [SyncRunner]'s call, not this class's.
 */
internal class FileActionRunner(
    private val storage: FServerStorage,
    private val localIndexer: LocalChangesIndexer,
    private val remoteFetcher: PeerIndexFetcher,
    private val fileUploader: FileUploader,
    private val fileDownloader: FileDownloader,
    private val timeProvider: TimeProvider,
    private val node: FilesNode,
) {
    suspend fun execute(source: SourceEntry, action: FileAction) = runBackgroundJob {
        when (action) {
            is FileAction.ComputeHash -> computeHash(action, source)
            is FileAction.Conflict -> resolveConflict(action, source)
            is FileAction.EvictLocal -> evictFile(action, source)
            is FileAction.DeleteLocal -> deleteLocalFile(action, source)
            is FileAction.DeleteRemote -> deleteRemoteFile(action, source)
            is FileAction.MergeVersion -> mergeVersion(action, source)
            is FileAction.Download -> downloadFile(action, source)
            is FileAction.Upload -> uploadFile(source, action)
        }
    }

    private suspend fun computeHash(
        action: FileAction.ComputeHash,
        source: SourceEntry,
    ) {
        // Only a present file has bytes to hash; the other side may be the deletion being weighed.
        if (action.local.content == null && action.local.state is FileRecord.State.Present) {
            localIndexer.hashFile(source, action.local)
        }

        if (action.remote.content == null && action.remote.state is FileRecord.State.Present) {
            val session = remoteFetcher.connectToDevice(source)

            session.runRemoteOperation(
                operation = RemoteOperation.File.Hash(
                    IndexedFileKey(fileId = action.id.value, sourceId = source.id)
                ),
            )
        }
    }

    private suspend fun resolveConflict(
        action: FileAction.Conflict,
        source: SourceEntry,
    ) {
        val resolution = (source.syncMode as? SyncMode.Mirror)?.conflictResolution
            ?: SyncMode.Mirror.ConflictResolution.LastWriteWins

        when (resolution) {
            SyncMode.Mirror.ConflictResolution.LastWriteWins ->
                transferWinner(action, source, localWins = lastWriteWins(action, source))

            SyncMode.Mirror.ConflictResolution.Ask -> applyDecision(action, source)
        }
    }

    private suspend fun lastWriteWins(action: FileAction.Conflict, source: SourceEntry): Boolean {
        val local = action.local.metadata.version
        val remote = action.remote.metadata.version

        return when {
            // If both sides have the same version, the device with the higher ID wins
            local == remote -> storage.identity.localDevice().deviceId > source.deviceId
            local == null -> false // No local version, remote wins
            remote == null -> true // Remote has no version, local wins
            else -> local.compareHlc(remote) // Last Write Wins based on HLC comparison
        }
    }

    /**
     * The conflict stays held until the user decides. A decision made over versions that have
     * changed since is dropped: the user never saw what it would now overwrite.
     */
    private suspend fun applyDecision(action: FileAction.Conflict, source: SourceEntry) {
        val key = IndexedFileKey(fileId = action.id.value, sourceId = source.id)
        val decision = storage.conflictDecisions.find(key) ?: return

        if (decision.local != action.local.seenVersion() || decision.remote != action.remote.seenVersion()) {
            // TODO: history entry - "your choice on <file> was dropped: it changed since".
            Timber.i("Dropping decision on ${action.local.path} in source ${source.id}: a side changed since")
            storage.conflictDecisions.remove(key)
            return
        }

        when (decision.choice) {
            ConflictDecision.Choice.KeepLocal -> transferWinner(action, source, localWins = true)

            ConflictDecision.Choice.KeepRemote -> transferWinner(action, source, localWins = false)

            ConflictDecision.Choice.KeepBoth -> {
                copyAside(action.local, source)
                // The copy is made: a retry after a failed transfer must not make another one.
                storage.conflictDecisions.put(decision.copy(choice = ConflictDecision.Choice.KeepRemote))
                transferWinner(action, source, localWins = false)
            }
        }

        storage.conflictDecisions.remove(key)
    }

    /**
     * Local bytes of [file] copied next to it as `<name> (<this device>).<ext>`. A new file: the
     * next scan versions it, and a pass sends it like any other.
     */
    private suspend fun copyAside(file: FileRecord, source: SourceEntry) {
        val locator = file.locator
            ?: error("Cannot copy local file ${file.id} because it has no locator")
        val label = storage.identity.localDevice().displayName

        withContext(Dispatchers.IO) {
            val fs = node.openSource(source.location.toFiles())
            val original = fs.openFile(locator) ?: throw FileNotFoundException(locator)

            val path = generateSequence(1) { it + 1 }
                .map { n -> ConflictCopies.path(file.path, label, n) }
                .first { !fs.fileExists(it) }

            val copy = fs.createFile(path)

            copy.openWriter().use { writer ->
                original.read().use { input ->
                    val buffer = ByteArray(CopyChunkSize)
                    var offset = 0L

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break

                        writer.write(offset, buffer, read)
                        offset += read
                    }
                }

                writer.sync()
            }

            Timber.i("Copied ${file.path} aside to $path in source ${source.id}")
        }
    }

    /**
     * Moves the winner's side over the loser's under the merged version; the winner's side adopts
     * it here, so both agree now rather than on the next pass.
     */
    private suspend fun transferWinner(
        action: FileAction.Conflict,
        source: SourceEntry,
        localWins: Boolean,
    ) {
        val local = action.local.metadata.version
        val remote = action.remote.metadata.version
        val mergedVersion = local?.merge(remote) ?: remote

        val key = IndexedFileKey(fileId = action.id.value, sourceId = source.id)

        if (localWins) {
            when (action.local.state) {
                is FileRecord.State.Present -> {
                    val sent = uploadFile(
                        source = source,
                        action = FileAction.Upload(
                            file = action.local,
                            version = mergedVersion,
                            reason = "Conflict resolution: local wins"
                        )
                    )

                    adoptLocally(
                        source = source,
                        key = key,
                        file = action.local,
                        version = mergedVersion,
                        expected = sent
                    )
                }

                is FileRecord.State.Deleted -> {
                    if (action.remote.state is FileRecord.State.Present) {
                        deleteRemoteFile(
                            action = FileAction.DeleteRemote(
                                file = action.remote,
                                version = mergedVersion,
                                reason = "Conflict resolution: local wins"
                            ),
                            source = source,
                        )

                        adoptLocally(
                            source = source,
                            key = key,
                            file = action.local,
                            version = mergedVersion,
                            expected = null
                        )
                    }
                }

                is FileRecord.State.Evicted -> {
                    // evicted version cannot win
                    Timber.w("Conflict resolution: evicted local ${action.local.path} cannot win over remote ${action.remote.path}")
                }
            }
        } else {
            when (action.remote.state) {
                is FileRecord.State.Present -> {
                    downloadFile(
                        action = FileAction.Download(
                            file = action.remote,
                            version = mergedVersion,
                            reason = "Conflict resolution: remote wins"
                        ),
                        source = source,
                    )

                    // An unhashed remote was hashed on the way: our copy holds its bytes now.
                    val received = action.remote.content ?: storage.index.findFile(key)?.hash
                    if (received != null) {
                        adoptRemotely(
                            source = source,
                            key = key,
                            file = action.remote,
                            version = mergedVersion,
                            expected = received
                        )
                    }
                }

                is FileRecord.State.Deleted -> {
                    if (action.local.state is FileRecord.State.Present) {
                        deleteLocalFile(
                            action = FileAction.DeleteLocal(
                                file = action.local,
                                version = mergedVersion,
                                reason = "Conflict resolution: remote wins"
                            ),
                            source = source,
                        )

                        adoptRemotely(
                            source = source,
                            key = key,
                            file = action.remote,
                            version = mergedVersion,
                            expected = null
                        )
                    }
                }

                is FileRecord.State.Evicted -> {
                    // evicted version cannot win
                    Timber.w("Conflict resolution: evicted remote ${action.remote.path} cannot win over local ${action.local.path}")
                }
            }
        }
    }

    private suspend fun evictFile(
        action: FileAction.EvictLocal,
        source: SourceEntry,
    ) {
        val locator = action.file.locator
            ?: error("Cannot delete local file ${action.file.id} because it has no locator")

        // skip checks, strategy knows what it's doing

        withContext(NonCancellable) {
            val fs = node.openSource(source.location.toFiles())
            // Nothing there is as good as deleted.
            val deleted = fs.openFile(locator)?.delete() ?: true

            if (!deleted) {
                Timber.w("Failed to evict file ${action.file.id} at ${action.file.path} from source ${source.id}")
                return@withContext
            }

            storage.index.updateFileState(
                key = IndexedFileKey(fileId = action.id.value, sourceId = source.id),
                state = LocalIndexedFile.State.Evicted(
                    evictedAt = timeProvider.now(),
                )
            )
        }
    }

    private suspend fun deleteLocalFile(
        action: FileAction.DeleteLocal,
        source: SourceEntry,
    ) {
        val locator = action.file.locator
            ?: error("Cannot delete local file ${action.file.id} because it has no locator")

        withContext(NonCancellable) {
            val fs = node.openSource(source.location.toFiles())
            // Nothing there is as good as deleted.
            val deleted = fs.openFile(locator)?.delete() ?: true

            if (!deleted) {
                Timber.w("Failed to delete file ${action.file.id} at ${action.file.path} from source ${source.id}")
                return@withContext
            }

            // The peer's deletion, not a new one of ours: recorded under its version.
            localIndexer.recordDeleted(
                source = source,
                key = IndexedFileKey(fileId = action.id.value, sourceId = source.id),
                version = action.version?.toIndexed(),
            )
        }
    }

    private suspend fun deleteRemoteFile(
        action: FileAction.DeleteRemote,
        source: SourceEntry,
    ) {
        val session = remoteFetcher.connectToDevice(source)

        session.runRemoteOperation(
            operation = RemoteOperation.File.Delete(
                key = IndexedFileKey(fileId = action.id.value, sourceId = source.id),
                version = action.version?.toDto(),
            ),
        )
    }

    /** Each side that does not hold the merged version yet records it; no bytes move. */
    private suspend fun mergeVersion(
        action: FileAction.MergeVersion,
        source: SourceEntry,
    ) {
        val key = IndexedFileKey(fileId = action.id.value, sourceId = source.id)

        adoptLocally(
            source = source,
            key = key,
            file = action.local,
            version = action.version,
            expected = action.local.content.takeUnless { action.local.state is FileRecord.State.Deleted },
        )

        adoptRemotely(
            source = source,
            key = key,
            file = action.remote,
            version = action.version,
            expected = action.remote.content.takeUnless { action.remote.state is FileRecord.State.Deleted },
        )
    }

    /** Records [version] for [file] here, unless it already has it. [expected] null means deleted. */
    private suspend fun adoptLocally(
        source: SourceEntry,
        key: IndexedFileKey,
        file: FileRecord,
        version: FileVersion?,
        expected: ContentHash?,
    ) {
        if (version == null || file.metadata.version == version) return

        localIndexer.adoptVersion(
            source = source,
            key = key,
            version = version.toIndexed(),
            expected = expected,
        )
    }

    /** Asks the peer to record [version] for [file], unless it already has it. */
    private suspend fun adoptRemotely(
        source: SourceEntry,
        key: IndexedFileKey,
        file: FileRecord,
        version: FileVersion?,
        expected: ContentHash?,
    ) {
        if (version == null || file.metadata.version == version) return

        val session = remoteFetcher.connectToDevice(source)

        session.runRemoteOperation(
            operation = RemoteOperation.File.AdoptVersion(
                key = key,
                version = version.toDto(),
                expected = expected?.let { ContentHashDto(value = it.value, algorithm = it.algorithm) },
            ),
        )
    }

    private suspend fun downloadFile(
        action: FileAction.Download,
        source: SourceEntry,
    ) {
        fileDownloader.download(
            source = source,
            key = IndexedFileKey(fileId = action.id.value, sourceId = source.id),
            sizeBytes = action.file.metadata.size,
            version = action.version?.toDto(),
        )
    }

    private suspend fun uploadFile(
        source: SourceEntry,
        action: FileAction.Upload
    ): ContentHash {
        val session = remoteFetcher.connectToDevice(source)
        return fileUploader.uploadFile(
            file = action.file,
            version = action.version,
            source = source,
            session = session,
        )
    }
}

private const val CopyChunkSize = 64 * 1024
