package com.fserver.files.upload.impl

import com.fserver.files.upload.Causality
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileRecord.State
import com.fserver.files.upload.FileVersion
import com.fserver.files.upload.FilesSnapshot
import com.fserver.files.upload.UploadDecisions
import com.fserver.files.upload.UploadStrategy
import com.fserver.files.upload.VersionVector

/**
 * Keeps the remote side holding exactly the local set, and vice versa.
 *
 * Reference implementation, kept deliberately small: it is here to show the shape of a strategy -
 * pure, snapshot in / decisions out - not to be the real sync policy. No batching, no caps, no
 * ordering guarantees, no partial-transfer handling.
 */
class MirrorUploadStrategy : UploadStrategy {

    /**
     * @param restoreMissingLocalFiles pull files that only the remote side has. Off means the
     *   mirror runs one-way and such files are deleted remotely instead.
     */
    data class Params(
        val restoreMissingLocalFiles: Boolean = true,
    ) : UploadStrategy.Params

    override fun accepts(params: UploadStrategy.Params): Boolean = params is Params

    override suspend fun plan(
        params: UploadStrategy.Params,
        snapshot: FilesSnapshot
    ): UploadDecisions {
        val params = params as? Params
            ?: throw IllegalArgumentException("MirrorUploadStrategy only accepts Params, got $params")

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
                    FileAction.Download(file = it, reason = "missing locally")
                } else {
                    FileAction.DeleteRemote(
                        file = it,
                        version = null,
                        reason = "one-way mirror, absent locally"
                    )
                }
            }

            remote == null -> uploadIfPossible(local, "missing remotely")

            else -> reconcile(local, remote)
        }

    /**
     * Version vectors say which side is newer; wall clocks never do. Content is compared first, so
     * sides holding the same bytes never trade them, whatever their history says.
     */
    private fun reconcile(local: FileRecord, remote: FileRecord): FileAction? {
        val localState = local.state
        val remoteState = remote.state

        // Eviction is not a deletion: the file stays in the set, so nothing propagates.
        if (localState is State.Evicted) return null

        val causality = local.vector.compare(remote.vector)

        return when {
            localState is State.Deleted && remoteState is State.Deleted ->
                mergeIfDiverged(local, remote, causality, "deleted on both sides")

            // One side deleted, the other kept or edited: the newer version decides.
            localState is State.Deleted || remoteState is State.Deleted -> when (causality) {
                Causality.Newer ->
                    if (localState is State.Deleted) {
                        hashBeforeDeleting(local, remote)
                            ?: FileAction.DeleteRemote(
                                remote,
                                local.metadata.version,
                                "deleted locally"
                            )
                    } else {
                        uploadIfPossible(local, "edited locally after remote deletion")
                    }

                Causality.Older ->
                    if (remoteState is State.Deleted) {
                        hashBeforeDeleting(local, remote)
                            ?: FileAction.DeleteLocal(
                                local,
                                remote.metadata.version,
                                "deleted remotely"
                            )
                    } else {
                        FileAction.Download(
                            file = remote,
                            reason = "edited remotely after local deletion"
                        )
                    }

                Causality.Equal, Causality.Concurrent ->
                    FileAction.Conflict(local, remote, "deleted on one side, edited on the other")
            }

            else -> when (compareContent(local, remote)) {
                ContentMatch.SAME -> mergeIfDiverged(local, remote, causality, "same content")

                // Cannot decide without a hash, and cannot compute one here: ask, then re-plan.
                ContentMatch.UNKNOWN -> FileAction.ComputeHash(
                    id = local.id,
                    local = local,
                    remote = remote,
                    reason = "not hashed yet",
                )

                ContentMatch.DIFFERENT -> when (causality) {
                    Causality.Newer -> uploadIfPossible(local, "newer locally")
                        ?: FileAction.Conflict(local, remote, "newer locally but bytes are evicted")

                    Causality.Older -> FileAction.Download(file = remote, reason = "newer remotely")

                    Causality.Concurrent -> FileAction.Conflict(
                        local,
                        remote,
                        "edited on both sides"
                    )

                    // Same history, different bytes: an edit one side never versioned.
                    Causality.Equal -> FileAction.Conflict(
                        local,
                        remote,
                        "same version, different content"
                    )
                }
            }
        }
    }

    /**
     * An unhashed file may hold an edit its version does not show yet - only hashing reveals it. So
     * the side about to lose its file is hashed first, and the plan re-made with what it finds.
     */
    private fun hashBeforeDeleting(local: FileRecord, remote: FileRecord): FileAction? {
        val survivor = if (local.state is State.Deleted) remote else local
        if (survivor.state !is State.Present || survivor.content != null) return null

        return FileAction.ComputeHash(
            id = local.id,
            local = local,
            remote = remote,
            reason = "may hold an unversioned edit",
        )
    }

    /** Same content, different histories: not a conflict, but both sides should record both. */
    private fun mergeIfDiverged(
        local: FileRecord,
        remote: FileRecord,
        causality: Causality,
        reason: String,
    ): FileAction? {
        if (causality == Causality.Equal) return null

        // Not equal, so at least one side has a version.
        val merged = listOfNotNull(local.metadata.version, remote.metadata.version)
            .reduce(FileVersion::merge)

        return FileAction.MergeVersion(local, remote, merged, reason)
    }

    /** A record with no version reads as never edited, so any versioned one is newer than it. */
    private val FileRecord.vector: VersionVector
        get() = metadata.version?.vector ?: VersionVector.Empty

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
            ?.let { FileAction.Upload(it, it.metadata.version, reason) }
}
