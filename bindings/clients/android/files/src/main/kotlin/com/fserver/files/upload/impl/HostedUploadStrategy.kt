package com.fserver.files.upload.impl

import com.fserver.files.upload.Causality
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileRecord.State
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.UploadDecisions
import com.fserver.files.upload.UploadStrategy

/**
 * The remote side holds the files, the local side only a cache of them.
 *
 * Remote changes always land here. Local edits reach the remote only when [Params.writable]; until
 * then they stay local, and a remote change overwrites them. A local copy the remote confirmably
 * holds is evicted, unless pinned or fetched on demand (the caller's TTL evicts those).
 */
class HostedUploadStrategy : UploadStrategy {

    /** @param writable local edits, new files and deletions may be sent to the remote. */
    data class Params(
        val writable: Boolean,
    ) : UploadStrategy.Params

    override fun accepts(params: UploadStrategy.Params): Boolean = params is Params

    override suspend fun plan(
        params: UploadStrategy.Params,
        snapshot: FilesSnapshot,
    ): UploadDecisions {
        require(params is Params) { "HostedUploadStrategy only accepts Params, got $params" }

        val actions = snapshot.join().mapNotNull { (_, local, remote) ->
            decide(params, local, remote)
        }
        return UploadDecisions(pairMoves(actions))
    }

    private fun decide(params: Params, local: FileRecord?, remote: FileRecord?): FileAction? =
        when (local?.state) {
            // Not cached: fetched on demand.
            null -> null

            is State.Present -> present(params, local, remote)

            is State.Deleted -> deleted(params, local, remote)

            is State.Evicted -> when (remote?.state) {
                // Eviction only follows a confirmed copy, so the remote deletion drops nothing of ours.
                is State.Deleted -> FileAction.DeleteLocal(local, remote.metadata.version, "deleted remotely")
                else -> null
            }
        }

    private fun present(params: Params, local: FileRecord, remote: FileRecord?): FileAction? {
        val causality = remote?.let(local::causality)

        return when (remote?.state) {
            null -> FileAction.Upload(local, local.metadata.version, "missing remotely").takeIf { params.writable }

            is State.Evicted -> null

            is State.Deleted -> when {
                causality == Causality.Newer ->
                    FileAction.Upload(local, local.metadata.version, "edited locally after remote deletion")
                        .takeIf { params.writable }

                params.writable && causality != Causality.Older ->
                    FileAction.Conflict(local, remote, "deleted remotely, edited locally")

                else -> hashBeforeDeleting(local, remote)
                    ?: FileAction.DeleteLocal(local, remote.metadata.version, "deleted remotely")
            }

            is State.Present -> when (compareContent(local, remote)) {
                ContentMatch.SAME -> mergeIfDiverged(local, remote, causality!!, "same content")
                    ?: evict(local)

                ContentMatch.UNKNOWN -> computeHash(local, remote, "not hashed yet")

                ContentMatch.DIFFERENT -> when {
                    causality == Causality.Newer ->
                        FileAction.Upload(local, local.metadata.version, "newer locally")
                            .takeIf { params.writable }

                    params.writable && causality != Causality.Older ->
                        FileAction.Conflict(local, remote, "edited on both sides")

                    else -> FileAction.Download(file = remote, reason = "newer remotely")
                }
            }
        }
    }

    private fun deleted(params: Params, local: FileRecord, remote: FileRecord?): FileAction? {
        remote ?: return null

        return when (remote.state) {
            is State.Deleted -> mergeIfDiverged(local, remote, local.causality(remote), "deleted on both sides")

            is State.Evicted -> null

            // An older local deletion is just a stale cache entry: the file is fetched on demand.
            is State.Present -> when (local.causality(remote)) {
                Causality.Newer -> FileAction.DeleteRemote(remote, local.metadata.version, "deleted locally")
                Causality.Older -> null
                Causality.Equal, Causality.Concurrent ->
                    FileAction.Conflict(local, remote, "deleted locally, edited remotely")
            }.takeIf { params.writable }
        }
    }

    private fun evict(local: FileRecord): FileAction? =
        FileAction.EvictLocal(local, "cached copy, held remotely").takeIf { local.evictable }

    /** An unhashed local file may hold an edit its version does not show yet. */
    private fun hashBeforeDeleting(local: FileRecord, remote: FileRecord): FileAction? =
        computeHash(local, remote, "may hold an unversioned edit").takeIf { local.content == null }
}
