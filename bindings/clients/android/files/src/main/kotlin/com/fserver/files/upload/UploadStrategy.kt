package com.fserver.files.upload

/**
 * Turns "what both sides have" into "what should happen", and nothing else.
 *
 * A strategy is a pure function over a [FilesSnapshot]: no network, no filesystem, no side effects.
 * The caller collects the snapshot, calls [plan], and executes the returned [UploadDecisions] however it likes.
 *
 * [P] is the strategy's own settings type.
 */
interface UploadStrategy<P : UploadStrategy.Params> {
    /**
     * Narrow untyped [Params] to this strategy's own type, or `null` if they belong to another
     * strategy. Replaces a `supports()` check followed by an unchecked cast at the call site.
     */
    fun accepts(params: Params): P?

    suspend fun plan(params: P, snapshot: FilesSnapshot): UploadDecisions

    /** Settings for one strategy: age thresholds, upload caps, conflict policy, etc. */
    interface Params
}

/**
 * Plan with untyped params, for a registry holding several strategies.
 * Returns `null` when [params] are not this strategy's.
 */
suspend fun <P : UploadStrategy.Params> UploadStrategy<P>.planIfAccepted(
    params: UploadStrategy.Params,
    snapshot: FilesSnapshot,
): UploadDecisions? = accepts(params)?.let { plan(it, snapshot) }
