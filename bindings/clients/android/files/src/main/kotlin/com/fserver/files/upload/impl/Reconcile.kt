package com.fserver.files.upload.impl

import com.fserver.files.upload.Causality
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileRecord.State
import com.fserver.files.upload.FileVersion
import com.fserver.files.upload.VersionVector

// Building blocks every strategy reconciles a file pair with.

/** A record with no version reads as never edited, so any versioned one is newer than it. */
internal val FileRecord.vector: VersionVector
    get() = metadata.version?.vector ?: VersionVector.Empty

internal fun FileRecord.causality(other: FileRecord): Causality = vector.compare(other.vector)

/** A plan may evict it: present, not pinned, and not a copy fetched on demand (the caller's TTL owns those). */
internal val FileRecord.evictable: Boolean
    get() = (state as? State.Present)?.let { !it.pinned && it.fetchedAt == null } == true

internal enum class ContentMatch { SAME, DIFFERENT, UNKNOWN }

/**
 * A differing size is a certain change even unhashed, so it is worth checking before asking for
 * a hash. An equal size proves nothing, which is what [ContentMatch.UNKNOWN] is for.
 */
internal fun compareContent(local: FileRecord, remote: FileRecord): ContentMatch {
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
internal fun canHash(local: FileRecord, remote: FileRecord): Boolean =
    listOf(local, remote).filter { it.content == null }.all { it.state is State.Present }

internal fun computeHash(local: FileRecord, remote: FileRecord, reason: String) =
    FileAction.ComputeHash(id = local.id, local = local, remote = remote, reason = reason)

/** Same content, different histories: not a conflict, but both sides should record both. */
internal fun mergeIfDiverged(
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

/**
 * Version that makes [winner]'s side newer than both: its own when it already is, both histories
 * merged otherwise. What a one-way mode forces the other side to take.
 */
internal fun dominatingVersion(winner: FileRecord, loser: FileRecord): FileVersion? {
    if (winner.causality(loser) == Causality.Newer) return winner.metadata.version

    return winner.metadata.version?.merge(loser.metadata.version) ?: loser.metadata.version
}
