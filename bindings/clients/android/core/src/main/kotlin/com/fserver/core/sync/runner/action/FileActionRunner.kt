package com.fserver.core.sync.runner.action

import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.sync.conflict.ConflictResolver
import com.fserver.core.sync.fileops.FileEvictor
import com.fserver.core.sync.fileops.FileMover
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalFileHasher
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.remote.PeerFileOperations
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import timber.log.Timber

/**
 * Carries out one planned [FileAction] and nothing more: checks it against [refusal], then hands it
 * to whoever does that kind of work. Ordering, retries and what a failure means are the pass's call.
 */
internal class FileActionRunner(
    private val steps: ActionSteps,
    private val conflicts: ConflictResolver,
    private val localHasher: LocalFileHasher,
    private val peerFiles: PeerFileOperations,
    private val fileEvictor: FileEvictor,
    private val fileMover: FileMover,
) {
    suspend fun execute(source: SourceEntry, action: FileAction): Result<Unit> = runBackgroundJob {
        refusal(source, action)?.let { why ->
            Timber.w("Refusing ${action::class.simpleName} on ${action.id.value} in source ${source.id}: $why (planned as: ${action.reason})")
            return@runBackgroundJob
        }

        when (action) {
            is FileAction.ComputeHash -> computeHash(action, source)

            is FileAction.Conflict -> conflicts.resolve(action, source)

            is FileAction.EvictLocal -> fileEvictor.evict(source, action.id.value, expected = action.file.content)

            is FileAction.DeleteLocal -> steps.deleteLocal(source, action.file, action.version)

            is FileAction.DeleteRemote -> steps.deleteRemote(source, action.file, action.version)

            is FileAction.MergeVersion -> mergeVersion(action, source)

            is FileAction.Download -> steps.download(source, action.file, action.version)

            is FileAction.Upload -> steps.upload(source, action.file, action.version)

            is FileAction.MoveLocal -> fileMover.move(
                source = source,
                from = IndexedFileKey(fileId = action.from.id.value, sourceId = source.id),
                expected = action.from.content!!,
                target = action.to,
                version = action.version,
                deletedVersion = action.deletedVersion,
            )

            is FileAction.MoveRemote -> peerFiles.move(source, action.from, action.to, action.version, action.deletedVersion)
        }
    }

    private suspend fun computeHash(action: FileAction.ComputeHash, source: SourceEntry) {
        // Only a present file has bytes to hash; the other side may be the deletion being weighed.
        if (action.local.content == null && action.local.state is FileRecord.State.Present) {
            localHasher.hashFile(source, action.local)
        }

        if (action.remote.content == null && action.remote.state is FileRecord.State.Present) {
            peerFiles.hash(source, action.id.value)
        }
    }

    /** Each side that does not hold the merged version yet records it; no bytes move. */
    private suspend fun mergeVersion(action: FileAction.MergeVersion, source: SourceEntry) {
        steps.adoptLocally(
            source = source,
            file = action.local,
            version = action.version,
            expected = action.local.content.takeUnless { action.local.state is FileRecord.State.Deleted },
        )

        steps.adoptRemotely(
            source = source,
            file = action.remote,
            version = action.version,
            expected = action.remote.content.takeUnless { action.remote.state is FileRecord.State.Deleted },
        )
    }
}
