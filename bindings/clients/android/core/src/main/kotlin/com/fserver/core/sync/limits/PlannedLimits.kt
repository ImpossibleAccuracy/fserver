package com.fserver.core.sync.limits

import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FilesSnapshot

/** [actions] split by this device's file limits: what the pass may run, and the downloads left out. */
internal data class LimitedPlan(
    val runnable: List<FileAction>,
    val overLimit: List<FileAction>,
)

/**
 * Drops the [FileAction.Download]s that would take this device past its limits: a new file, or a
 * held one grown too big. Newest files taken first. Uploads pass untouched: the peer checks them
 * against its own limits on receipt.
 */
internal fun SourceEntry.Preferences.FileLimits.limit(
    snapshot: FilesSnapshot,
    actions: List<FileAction>,
): LimitedPlan {
    if (this == SourceEntry.Preferences.FileLimits.None) return LimitedPlan(actions, emptyList())

    val held = snapshot.local
        .filter { it.state is FileRecord.State.Present }
        .associate { it.id.value to it.metadata.size }

    val budget = FileBudget(this, SourceUsage(files = held.size, bytes = held.values.sum()))

    // Updates first: a held file going stale is worse than a new one waiting. Newest first within each.
    val (updates, fresh) = actions.filterIsInstance<FileAction.Download>()
        .sortedByDescending { it.file.metadata.lastModified }
        .partition { it.id.value in held }

    val overLimit = updates.filterNot { budget.admitUpdate(held.getValue(it.id.value), it.file.metadata.size) }
        .plus(fresh.filterNot { budget.admitNew(it.file.metadata.size) })
        .toSet()

    return LimitedPlan(
        runnable = actions.filterNot { it in overLimit },
        overLimit = actions.filter { it in overLimit },
    )
}
