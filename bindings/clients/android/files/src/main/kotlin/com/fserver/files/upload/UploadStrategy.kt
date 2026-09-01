package com.fserver.files.upload

/**
 * Turns "what both sides have" into "what should happen", and nothing else.
 *
 * A strategy is a pure function over a [FilesSnapshot]: no network, no filesystem, no side effects.
 * The caller collects the snapshot, calls [plan], and executes the returned [UploadDecisions] however it likes.
 */
interface UploadStrategy {
    /**
     * Narrow untyped [Params] to this strategy's own type, or `null` if they belong to another
     * strategy. Replaces a `supports()` check followed by an unchecked cast at the call site.
     */
    fun accepts(params: Params): Boolean

    suspend fun plan(params: Params, snapshot: FilesSnapshot): UploadDecisions

    /** Settings for one strategy: age thresholds, upload caps, conflict policy, etc. */
    interface Params
}
