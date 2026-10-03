package com.fserver.core.sync.conflict

import com.fserver.core.sync.model.SyncMode
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.UploadDecisions

/**
 * Decisions a pass will never carry out: the file no longer conflicts - the peer's own decision
 * got there first, or someone edited past it - or the source stopped asking, its peer's clock
 * included.
 *
 * A file waiting on a hash may still turn out to conflict, so its decision stays for now.
 */
internal fun settledDecisions(
    resolution: SyncMode.Mirror.ConflictResolution?,
    plan: UploadDecisions,
    stored: List<ConflictDecision>,
): List<ConflictDecision> {
    if (resolution != SyncMode.Mirror.ConflictResolution.Ask) return stored

    val open = plan.actions
        .filter { it is FileAction.Conflict || it is FileAction.ComputeHash }
        .mapTo(HashSet()) { it.id.value }

    return stored.filterNot { it.fileId in open }
}
