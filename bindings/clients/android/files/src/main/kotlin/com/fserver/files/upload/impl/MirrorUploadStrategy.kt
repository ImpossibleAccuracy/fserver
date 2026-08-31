package com.fserver.files.upload.impl

import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileRecord.State
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.UploadDecisions
import com.fserver.files.upload.UploadStrategy

/**
 * Keeps the remote side holding exactly the local set, and vice versa.
 *
 * Reference implementation, kept deliberately small: it is here to show the shape of a strategy -
 * pure, snapshot in / decisions out - not to be the real sync policy. No batching, no caps, no
 * ordering guarantees, no partial-transfer handling.
 */
class MirrorUploadStrategy : UploadStrategy<MirrorUploadStrategy.Params> {

    /**
     * @param restoreMissingLocalFiles pull files that only the remote side has. Off means the
     *   mirror runs one-way and such files are deleted remotely instead.
     */
    data class Params(
        val restoreMissingLocalFiles: Boolean = true,
    ) : UploadStrategy.Params

    override fun accepts(params: UploadStrategy.Params): Params? = params as? Params

    override suspend fun plan(params: Params, snapshot: FilesSnapshot): UploadDecisions {
        val actions = snapshot.join().mapNotNull { (_, local, remote) ->
            decide(params, local, remote)
        }
        return UploadDecisions(actions)
    }

    private fun decide(params: Params, local: FileRecord?, remote: FileRecord?): FileAction? =
        when {
            local == null && remote == null -> null

            // Remote-only file: either it never reached us, or we dropped it. Both look the same here,
            // which is exactly why a local tombstone has to survive long enough to be seen below.
            local == null -> remote!!.takeIf { it.state !is State.Deleted }?.let {
                if (params.restoreMissingLocalFiles) {
                    FileAction.Download(it, "missing locally")
                } else {
                    FileAction.DeleteRemote(it, "one-way mirror, absent locally")
                }
            }

            remote == null -> uploadIfPossible(local, "missing remotely")

            else -> reconcile(local, remote)
        }

    private fun reconcile(local: FileRecord, remote: FileRecord): FileAction? {
        val localState = local.state
        val remoteState = remote.state

        return when {
            // Eviction is not a deletion: the file stays in the set, so nothing propagates.
            localState is State.Evicted -> null

            localState is State.Deleted && remoteState is State.Deleted -> null

            localState is State.Deleted ->
                FileAction.DeleteRemote(remote, "deleted locally at ${localState.deletedAt}")

            remoteState is State.Deleted ->
                FileAction.DeleteLocal(local, "deleted remotely at ${remoteState.deletedAt}")

            else -> when (compareContent(local, remote)) {
                ContentMatch.SAME -> null

                ContentMatch.DIFFERENT -> resolveDivergence(local, remote)

                // Cannot decide without a hash, and cannot compute one here: ask, then re-plan.
                ContentMatch.UNKNOWN -> FileAction.ComputeHash(
                    file = if (local.content == null) local else remote,
                    reason = "not hashed yet",
                )
            }
        }
    }

    /**
     * Newest write wins, and only when the sides agree on who wrote last. Everything else is
     * reported instead of guessed - a real strategy would take a policy here.
     */
    private fun resolveDivergence(local: FileRecord, remote: FileRecord): FileAction {
        val localRevision = local.metadata.revision
        val remoteRevision = remote.metadata.revision

        val concurrentEdit = localRevision != null &&
                remoteRevision != null &&
                localRevision.originDevice != remoteRevision.originDevice

        if (concurrentEdit) {
            return FileAction.Conflict(local, remote, "edited on both sides")
        }

        val localIsNewer = local.metadata.lastModified > remote.metadata.lastModified
        return if (localIsNewer) {
            uploadIfPossible(local, "newer locally")
                ?: FileAction.Conflict(local, remote, "newer locally but bytes are evicted")
        } else {
            FileAction.Download(remote, "newer remotely")
        }
    }

    private enum class ContentMatch { SAME, DIFFERENT, UNKNOWN }

    /**
     * A differing size is a certain change even unhashed, so it is worth checking before asking for
     * a hash. An equal size proves nothing, which is what [ContentMatch.UNKNOWN] is for.
     */
    private fun compareContent(local: FileRecord, remote: FileRecord): ContentMatch {
        val localContent = local.content
        val remoteContent = remote.content

        return when {
            localContent != null && remoteContent != null ->
                if (localContent == remoteContent) ContentMatch.SAME else ContentMatch.DIFFERENT

            local.metadata.size != remote.metadata.size -> ContentMatch.DIFFERENT

            else -> ContentMatch.UNKNOWN
        }
    }

    /** Only a [State.Present] file has bytes to send; an evicted one knows its hash but not itself. */
    private fun uploadIfPossible(local: FileRecord, reason: String): FileAction? =
        local.takeIf { it.state is State.Present }
            ?.let { FileAction.Upload(it, reason) }
}
