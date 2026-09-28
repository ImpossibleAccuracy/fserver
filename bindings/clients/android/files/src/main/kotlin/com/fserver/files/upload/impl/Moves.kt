package com.fserver.files.upload.impl

import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord.State
import java.util.Collections
import java.util.IdentityHashMap

/**
 * A rename reaches a plan as a new file sent plus an old one deleted, under different ids. Where
 * both hold the same bytes, the side with the old file renames it instead of receiving a copy.
 */
internal fun pairMoves(actions: List<FileAction>): List<FileAction> {
    val remoteDeletes = actions.filterIsInstance<FileAction.DeleteRemote>()
        .filter { it.file.state is State.Present && it.file.content != null }
        .groupByTo(HashMap(), { it.file.content!! }) { it }

    val localDeletes = actions.filterIsInstance<FileAction.DeleteLocal>()
        .filter { it.file.state is State.Present && it.file.content != null && it.file.locator != null }
        .groupByTo(HashMap(), { it.file.content!! }) { it }

    if (remoteDeletes.isEmpty() && localDeletes.isEmpty()) return actions

    val paired = Collections.newSetFromMap(IdentityHashMap<FileAction, Boolean>())

    val moved = actions.map { action ->
        when (action) {
            is FileAction.Upload -> {
                val delete = action.file.content?.let { remoteDeletes[it]?.removeFirstOrNull() }
                    ?: return@map action
                paired += delete

                FileAction.MoveRemote(
                    from = delete.file,
                    to = action.file,
                    version = action.version,
                    deletedVersion = delete.version,
                    reason = "renamed from ${delete.file.path}",
                )
            }

            is FileAction.Download -> {
                val delete = action.file.content?.let { localDeletes[it]?.removeFirstOrNull() }
                    ?: return@map action
                paired += delete

                FileAction.MoveLocal(
                    from = delete.file,
                    to = action.file,
                    version = action.version,
                    deletedVersion = delete.version,
                    reason = "renamed from ${delete.file.path}",
                )
            }

            else -> action
        }
    }

    return moved.filterNot { it in paired }
}
