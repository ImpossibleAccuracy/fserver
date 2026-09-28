package com.fserver.core.sync.conflict

import com.fserver.common.model.FileSize
import com.fserver.core.sync.index.LocalIndexedFile
import kotlin.time.Instant

/** A file both sides changed, held until the user decides. See [ConflictsController]. */
data class FileConflict(
    val sourceId: String,
    val fileId: String,
    /** Source-relative path, the same on both sides. */
    val path: String,
    /** This device's side. */
    val local: Side,
    /** The source peer's side. */
    val remote: Side,
) {
    data class Side(
        val deviceId: String,
        /** [LocalIndexedFile.State.Deleted] when this side's change was deleting the file. */
        val state: LocalIndexedFile.State,
        val size: FileSize,
        val modifiedAt: Instant,
        /** Who made this side's version and when, or null when it has none. */
        val version: LocalIndexedFile.Version?,
    )

    /** What the user may pick: a side whose bytes were evicted has nothing to keep. */
    val choices: Set<ConflictDecision.Choice>
        get() = conflictChoices(
            localEvicted = local.state is LocalIndexedFile.State.Evicted,
            remoteEvicted = remote.state is LocalIndexedFile.State.Evicted,
            bothPresent = local.state is LocalIndexedFile.State.Present && remote.state is LocalIndexedFile.State.Present,
        )
}

/** The one rule for what a conflict offers, over index rows and plan records alike. */
internal fun conflictChoices(
    localEvicted: Boolean,
    remoteEvicted: Boolean,
    bothPresent: Boolean,
): Set<ConflictDecision.Choice> = buildSet {
    if (!localEvicted) add(ConflictDecision.Choice.KeepLocal)
    if (!remoteEvicted) add(ConflictDecision.Choice.KeepRemote)
    if (bothPresent) add(ConflictDecision.Choice.KeepBoth)
}
