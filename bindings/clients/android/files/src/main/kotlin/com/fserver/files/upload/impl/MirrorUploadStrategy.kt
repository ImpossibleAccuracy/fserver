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
 * An evicted side stays in the set but has no bytes: it never sends, is never refilled (it catches
 * up on demand), never turns into a deletion, and is never deleted by anything but a newer
 * deletion. Where it holds the only newer version against the other side's bytes, the plan says so
 * with a [FileAction.Conflict] rather than silently.
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

            local == null -> remoteOnly(params, remote!!)

            // Evicted with no remote record: the bytes are gone from both sides, nothing to send.
            // Deleted with no remote record: the remote never had it.
            remote == null ->
                local.takeIf { it.state is State.Present }
                    ?.let { FileAction.Upload(it, it.metadata.version, "missing remotely") }

            else -> reconcile(local, remote)
        }

    /**
     * Remote-only file: either it never reached us, or we dropped it. Both look the same here,
     * which is exactly why a local tombstone has to survive long enough to be seen in [reconcile].
     */
    private fun remoteOnly(params: Params, remote: FileRecord): FileAction? = when {
        remote.state is State.Deleted -> null

        // Evicted there: nothing to pull, and its bytes live on a backup a deletion would reach.
        remote.state is State.Evicted -> null

        !params.restoreMissingLocalFiles -> FileAction.DeleteRemote(
            file = remote,
            version = null,
            reason = "one-way mirror, absent locally",
        )

        else -> FileAction.Download(file = remote, reason = "missing locally")
    }

    /**
     * Version vectors say which side is newer; wall clocks never do. Content is compared first, so
     * sides holding the same bytes never trade them, whatever their history says.
     */
    private fun reconcile(local: FileRecord, remote: FileRecord): FileAction? {
        val causality = local.vector.compare(remote.vector)
        val localDeleted = local.state is State.Deleted
        val remoteDeleted = remote.state is State.Deleted

        return when {
            localDeleted && remoteDeleted ->
                mergeIfDiverged(local, remote, causality, "deleted on both sides")

            localDeleted || remoteDeleted -> reconcileDeletion(local, remote, causality)

            else -> reconcileContent(local, remote, causality)
        }
    }

    /** One side deleted, the other kept, edited or evicted: the newer version decides. */
    private fun reconcileDeletion(
        local: FileRecord,
        remote: FileRecord,
        causality: Causality,
    ): FileAction? {
        val localDeleted = local.state is State.Deleted
        val survivor = if (localDeleted) remote else local
        val deletionNewer = causality == (if (localDeleted) Causality.Newer else Causality.Older)

        // Evicted survivor: neither side has bytes. Only a newer deletion may act - it drops a
        // record, not data. Otherwise the bytes may still live on the survivor's backup, which a
        // deletion from here would reach.
        if (survivor.state is State.Evicted && !deletionNewer) return null

        return when (causality) {
            Causality.Newer ->
                if (localDeleted) {
                    hashBeforeDeleting(local, remote)
                        ?: FileAction.DeleteRemote(remote, local.metadata.version, "deleted locally")
                } else {
                    sendNewer(local, remote, "edited locally after remote deletion")
                }

            Causality.Older ->
                if (localDeleted) {
                    receiveNewer(local, remote, "edited remotely after local deletion")
                } else {
                    hashBeforeDeleting(local, remote)
                        ?: FileAction.DeleteLocal(local, remote.metadata.version, "deleted remotely")
                }

            Causality.Equal, Causality.Concurrent ->
                FileAction.Conflict(local, remote, "deleted on one side, edited on the other")
        }
    }

    /** Neither side deleted; either may be evicted. */
    private fun reconcileContent(
        local: FileRecord,
        remote: FileRecord,
        causality: Causality,
    ): FileAction? {
        val match = compareContent(local, remote)
        if (match == ContentMatch.SAME) return mergeIfDiverged(local, remote, causality, "same content")

        // No bytes on either side: no transfer can happen and no choice can be offered.
        if (local.state is State.Evicted && remote.state is State.Evicted) return null

        if (match == ContentMatch.UNKNOWN && canHash(local, remote)) {
            // Cannot decide without a hash, and cannot compute one here: ask, then re-plan.
            return FileAction.ComputeHash(
                id = local.id,
                local = local,
                remote = remote,
                reason = "not hashed yet",
            )
        }

        // From here content is either known to differ or can never be known: versions decide alone.
        return when (causality) {
            Causality.Newer -> sendNewer(local, remote, "newer locally")

            Causality.Older -> receiveNewer(local, remote, "newer remotely")

            Causality.Concurrent -> FileAction.Conflict(local, remote, "edited on both sides")

            // Same history, different bytes: an edit one side never versioned. Unhashable content
            // under the same history is taken as the same.
            Causality.Equal -> FileAction.Conflict(local, remote, "same version, different content")
                .takeIf { match == ContentMatch.DIFFERENT }
        }
    }

    /** Local holds the newer version: send it, unless one side has no bytes. */
    private fun sendNewer(local: FileRecord, remote: FileRecord, reason: String): FileAction? =
        when {
            local.state is State.Evicted ->
                FileAction.Conflict(local, remote, "$reason, but local bytes are evicted")

            remote.state is State.Evicted -> null

            else -> FileAction.Upload(local, local.metadata.version, reason)
        }

    /** Remote holds the newer version: pull it, unless one side has no bytes. */
    private fun receiveNewer(local: FileRecord, remote: FileRecord, reason: String): FileAction? =
        when {
            remote.state is State.Evicted ->
                FileAction.Conflict(local, remote, "$reason, but remote bytes are evicted")

            local.state is State.Evicted -> null

            else -> FileAction.Download(file = remote, reason = reason)
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

    /** Hashing reads bytes, so it only helps when every unhashed side still has them. */
    private fun canHash(local: FileRecord, remote: FileRecord): Boolean =
        listOf(local, remote).filter { it.content == null }.all { it.state is State.Present }
}
