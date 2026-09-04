package com.fserver.core.sync.runner

import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.utils.runRemoteOperation
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncPreferences
import com.fserver.core.sync.index.IndexedFile
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileAction
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
            is FileAction.Download -> downloadFile(action, source)
            is FileAction.Upload -> {
                val session = remoteFetcher.connectToDevice(source)
                fileUploader.uploadFile(
                    file = action.file,
                    source = source,
                    session = session,
                )
            }
        }
    }

    private suspend fun computeHash(
        action: FileAction.ComputeHash,
        source: SourceEntry,
    ) {
        if (action.local.content == null) {
            localIndexer.hashFile(source, action.local)
        }

        if (action.remote.content == null) {
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
        val prefs = storage.preferences.getSourceRules()

        when (prefs.conflictResolution) {
            SyncPreferences.ConflictResolution.LastWriteWins -> {
                // TODO: save winner and delete loser
            }

            SyncPreferences.ConflictResolution.KeepBoth -> {
                // TODO: save both variants to .conflict folder
            }
        }

        Timber.w("Conflict resolution not implemented yet for ${action.local.path} and ${action.remote.path}")
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
            val deleted = fs.deleteFile(locator)

            if (!deleted) {
                Timber.w("Failed to evict file ${action.file.id} at ${action.file.path} from source ${source.id}")
                return@withContext
            }

            storage.index.updateFileState(
                key = IndexedFileKey(fileId = action.id.value, sourceId = source.id),
                state = IndexedFile.State.Evicted(
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
            val deleted = fs.deleteFile(locator)

            if (!deleted) {
                Timber.w("Failed to delete file ${action.file.id} at ${action.file.path} from source ${source.id}")
                return@withContext
            }

            storage.index.updateFileState(
                key = IndexedFileKey(fileId = action.id.value, sourceId = source.id),
                state = IndexedFile.State.Deleted(
                    deletedAt = timeProvider.now(),
                ),
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
                IndexedFileKey(fileId = action.id.value, sourceId = source.id)
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
                IndexedFileKey(fileId = action.id.value, sourceId = source.id)
            ),
            timeout = downloadTimeout(action.file.metadata.size),
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
