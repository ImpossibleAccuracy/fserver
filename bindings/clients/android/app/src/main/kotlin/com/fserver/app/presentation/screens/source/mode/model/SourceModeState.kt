package com.fserver.app.presentation.screens.source.mode.model

import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.composable.modes

/**
 * One mode per source, picked once access is in hand.
 *
 * The list is ordered from the safest to the most irreversible, and the recommended one is
 * selected up front — the difference between the modes is one sentence about what happens to
 * the original.
 */
data class SourceModeState(
    val kind: SourceKindUi,
    val access: SourceAccessUi = SourceAccessUi.Full,
    val selected: SourceModeUi? = null,
    /** How many items a partial grant covers. Only the photos branch can be partial. */
    val grantedItemCount: Int = 0,
    /** What the branch got hold of: a folder path, or the device — blank for photos. */
    val sourceLabel: String = "",
    /** What the access step's scan counted. Zero until a branch actually walks something. */
    val sourceFiles: Int = 0,
    val sourceBytes: Long = 0,
) {
    val modes: List<SourceModeUi> = kind.modes

    val isPartial: Boolean get() = access == SourceAccessUi.Partial

    val canContinue: Boolean get() = selected != null
}
