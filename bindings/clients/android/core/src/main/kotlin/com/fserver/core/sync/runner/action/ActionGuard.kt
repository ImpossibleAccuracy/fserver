package com.fserver.core.sync.runner.action

import com.fserver.core.sync.conflict.choices
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.drivesSync
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileRecord

/**
 * Why [action] must not run on [source], or null. Any refusal is a strategy bug: this is the last
 * line before bytes are destroyed. An end that does not drive the source acts on nothing.
 */
internal fun refusal(source: SourceEntry, action: FileAction): String? =
    if (!source.drivesSync) "${source.syncMode.type} runs from the initiator"
    else refusal(action)

/** Why [action] contradicts the records it carries, or null. */
private fun refusal(action: FileAction): String? = when (action) {
    is FileAction.Upload -> "no local bytes to send".takeUnless { action.file.state is FileRecord.State.Present }

    is FileAction.Download -> "no remote bytes to pull".takeUnless { action.file.state is FileRecord.State.Present }

    is FileAction.EvictLocal -> when (val state = action.file.state) {
        !is FileRecord.State.Present -> "not present"
        else -> when {
            state.pinned -> "pinned"
            action.file.content == null -> "not hashed, so no copy can be confirmed"
            else -> null
        }
    }

    is FileAction.MergeVersion -> {
        val localDeleted = action.local.state is FileRecord.State.Deleted
        when {
            localDeleted != (action.remote.state is FileRecord.State.Deleted) -> "only one side is deleted"
            !localDeleted && (action.local.content == null || action.local.content != action.remote.content) ->
                "content not known to match"

            else -> null
        }
    }

    is FileAction.Conflict -> "neither side can be kept".takeIf { action.choices().isEmpty() }

    is FileAction.MoveLocal -> moveRefusal(action.from, action.to)

    is FileAction.MoveRemote -> moveRefusal(action.from, action.to)

    is FileAction.ComputeHash, is FileAction.DeleteLocal, is FileAction.DeleteRemote -> null
}

private fun moveRefusal(from: FileRecord, to: FileRecord): String? = when {
    from.state !is FileRecord.State.Present || to.state !is FileRecord.State.Present -> "a side has no bytes"
    from.content == null || from.content != to.content -> "content not known to match"
    else -> null
}
