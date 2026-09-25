package com.fserver.core.sync.runner

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.ContentHashDto
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.network.utils.runRemoteOperation
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.toIndexed
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Carries out one planned [FileAction] and nothing more.
 * Ordering, retries and what a failure means are [SyncRunner]'s call, not this class's.
 */
internal class FileActionRunner(
    private val storage: FServerStorage,
    private val localIndexer: LocalChangesIndexer,
    private val remoteFetcher: PeerIndexFetcher,
    private val fileUploader: FileUploader,
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
        when (source.preferences.conflictResolution) {
            SourceEntry.Preferences.ConflictResolution.LastWriteWins -> {
                val local = action.local.metadata.version
                val remote = action.remote.metadata.version

                val localWins = when {
                    local == remote ->
                        // If both sides have the same version, the device with the higher ID wins
                        storage.identity.localDevice().deviceId > source.deviceId

                    local == null -> false // No local version, remote wins
                    remote == null -> true // Remote has no version, local wins
                    else -> local.compareHlc(remote) // Last Write Wins based on HLC comparison
                }

                val mergedVersion = local?.merge(remote) ?: remote

                val key = IndexedFileKey(fileId = action.id.value, sourceId = source.id)

                // The transfer leaves the loser's side at mergedVersion; the winner's side adopts it
                // here, so both agree now rather than on the next pass.
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
                            Timber.w("Conflict resolution: evicted local file ${action.local.path} win LWW over remote ${action.remote.path}")
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
                            Timber.w("Conflict resolution: evicted remote file ${action.remote.path} win LWW over local ${action.local.path}")
                        }
                    }
                }
            }

            SourceEntry.Preferences.ConflictResolution.KeepBoth -> {
                Timber.w("Conflict resolution not implemented yet for ${action.local.path} and ${action.remote.path}")
                // TODO: save both variants to .conflict folder
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
        val session = remoteFetcher.connectToDevice(source)

        session.runRemoteOperation(
            operation = RemoteOperation.File.Download(
                key = IndexedFileKey(fileId = action.id.value, sourceId = source.id),
                version = action.version?.toDto(),
            ),
            timeout = downloadTimeout(action.file.metadata.size),
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

    /**
     * The peer confirms a download only once the whole file is across, so a fixed timeout fails
     * big transfers that are working fine. Budgeted from size against a pessimistic link instead.
     */
    private fun downloadTimeout(sizeBytes: Long): Duration =
        (MinDownloadTimeout + (sizeBytes / SlowestExpectedBytesPerSecond).seconds)
            .coerceAtMost(MaxDownloadTimeout)

    companion object {
        // TODO: make configurable
        private val MinDownloadTimeout = 2.minutes
        private val MaxDownloadTimeout = 2.hours
        private const val SlowestExpectedBytesPerSecond = 64L * 1024
    }
}
