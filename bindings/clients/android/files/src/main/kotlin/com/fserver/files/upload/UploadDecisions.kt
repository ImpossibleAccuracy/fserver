package com.fserver.files.upload

/**
 * What a strategy wants done, in no particular order.
 *
 * One flat list of [FileAction] instead of one list per verb: adding a verb no longer widens the
 * type, a file can no longer land in two lists with contradictory meanings, and every action keeps
 * the record it was decided from.
 */
data class UploadDecisions(
    val actions: List<FileAction>,
) {
    val isEmpty: Boolean get() = actions.isEmpty()

    inline fun <reified T : FileAction> filterIsAction(): List<T> = actions.filterIsInstance<T>()

    companion object {
        val Empty = UploadDecisions(emptyList())
    }
}

/**
 * A single unit of work for the executor. Describes intent only - who moves the bytes, and in what
 * order, is the caller's problem.
 */
sealed interface FileAction {
    val id: FileId

    /** Diagnostics only. Never branch on it. */
    val reason: String

    /** Send local bytes to the remote side. [file] must be [FileRecord.State.Present]. */
    data class Upload(val file: FileRecord, override val reason: String) : FileAction {
        override val id: FileId get() = file.id
    }

    /** Pull remote bytes down. [FileRecord.path] says where they go. */
    data class Download(val file: FileRecord, override val reason: String) : FileAction {
        override val id: FileId get() = file.id
    }

    /** Propagate a user deletion to the remote side. */
    data class DeleteRemote(val file: FileRecord, override val reason: String) : FileAction {
        override val id: FileId get() = file.id
    }

    /** Propagate a remote user deletion locally. */
    data class DeleteLocal(val file: FileRecord, override val reason: String) : FileAction {
        override val id: FileId get() = file.id
    }

    /**
     * Free local bytes, keep the file in the set.
     * Distinct from [DeleteLocal] on purpose: an executor that maps this onto a deleted that
     * later propagates destroys user data.
     */
    data class EvictLocal(val file: FileRecord, override val reason: String) : FileAction {
        override val id: FileId get() = file.id
    }

    /**
     * The plan is blocked on a [FileRecord.content] record:
     * - for a local record the executor reads the bytes;
     * - for a remote one it asks the peer;
     *
     * Hash the file, store the hash in the index, re-run.
     *
     * A verb rather than a silent assumption, because both assumptions are wrong:
     * "unhashed means equal" skips real changes, "unhashed means different" re-uploads the whole set every scan.
     */
    data class ComputeHash(val file: FileRecord, override val reason: String) : FileAction {
        override val id: FileId get() = file.id
    }

    /** Both sides changed and the strategy refuses to guess. Needs a human or a policy above. */
    data class Conflict(
        val local: FileRecord,
        val remote: FileRecord,
        override val reason: String,
    ) : FileAction {
        override val id: FileId get() = local.id
    }
}
