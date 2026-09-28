package com.fserver.files.upload.impl

import com.fserver.files.upload.Causality
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileRecord.State
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.UploadDecisions
import com.fserver.files.upload.UploadStrategy
import kotlin.time.Instant

/**
 * Pushes the local set to the remote side and never takes anything back: remote edits and
 * deletions are overwritten by the local version, remote-only files are left alone. No
 * [FileAction.Download], [FileAction.DeleteLocal] or [FileAction.Conflict] is ever planned.
 *
 * With [Params.evictWhen], local bytes the remote confirmably holds are freed afterwards.
 */
class OneWayUploadStrategy : UploadStrategy {

    /**
     * @param propagateDeletions a local deletion deletes the remote copy too. Off keeps the remote
     *   as an archive of everything ever sent.
     * @param skipModifiedBefore a file the remote never had is not sent when last modified before this.
     * @param evictWhen which local files to free once the remote holds them; null keeps them all.
     *   Pinned and fetched-on-demand files are never evicted by the plan.
     */
    data class Params(
        val propagateDeletions: Boolean,
        val skipModifiedBefore: Instant? = null,
        val evictWhen: EvictCriterion? = null,
    ) : UploadStrategy.Params

    sealed interface EvictCriterion {
        data class ModifiedBefore(val instant: Instant) : EvictCriterion

        data class LargerThan(val bytes: Long) : EvictCriterion
    }

    override fun accepts(params: UploadStrategy.Params): Boolean = params is Params

    override suspend fun plan(
        params: UploadStrategy.Params,
        snapshot: FilesSnapshot,
    ): UploadDecisions {
        require(params is Params) { "OneWayUploadStrategy only accepts Params, got $params" }

        val actions = snapshot.join().mapNotNull { (_, local, remote) ->
            decide(params, local, remote)
        }
        return UploadDecisions(pairMoves(actions))
    }

    private fun decide(params: Params, local: FileRecord?, remote: FileRecord?): FileAction? =
        when (local?.state) {
            // Remote-only: whatever the remote holds on its own is its business.
            null -> null

            is State.Present -> present(params, local, remote)

            is State.Deleted -> deleted(params, local, remote)

            // Bytes live on the remote alone: nothing to send, and nothing may come back.
            is State.Evicted -> null
        }

    private fun present(params: Params, local: FileRecord, remote: FileRecord?): FileAction? =
        when (remote?.state) {
            null ->
                if (params.skipModifiedBefore?.let { local.metadata.lastModified < it } == true) null
                else FileAction.Upload(local, local.metadata.version, "missing remotely")

            is State.Deleted ->
                FileAction.Upload(local, dominatingVersion(local, remote), "deleted remotely, kept locally")

            // Only this side evicts in a one-way mode.
            is State.Evicted -> null

            is State.Present -> when (compareContent(local, remote)) {
                ContentMatch.SAME ->
                    mergeIfDiverged(local, remote, local.causality(remote), "same content")
                        ?: evict(params, local)

                // Both sides present, so both can be hashed.
                ContentMatch.UNKNOWN -> computeHash(local, remote, "not hashed yet")

                ContentMatch.DIFFERENT -> FileAction.Upload(
                    file = local,
                    version = dominatingVersion(local, remote),
                    reason = when (local.causality(remote)) {
                        Causality.Newer -> "newer locally"
                        else -> "overwrites a remote edit"
                    },
                )
            }
        }

    private fun deleted(params: Params, local: FileRecord, remote: FileRecord?): FileAction? =
        when (remote?.state) {
            null, is State.Evicted -> null

            is State.Deleted -> mergeIfDiverged(local, remote, local.causality(remote), "deleted on both sides")

            is State.Present -> FileAction.DeleteRemote(
                file = remote,
                version = dominatingVersion(local, remote),
                reason = "deleted locally",
            ).takeIf { params.propagateDeletions }
        }

    /** Called only once both sides are known to hold the same bytes. */
    private fun evict(params: Params, local: FileRecord): FileAction? {
        val criterion = params.evictWhen ?: return null
        if (!local.evictable) return null

        val due = when (criterion) {
            is EvictCriterion.ModifiedBefore -> local.metadata.lastModified < criterion.instant
            is EvictCriterion.LargerThan -> local.metadata.size > criterion.bytes
        }

        return if (due) FileAction.EvictLocal(local, "confirmed remotely, $criterion") else null
    }
}
