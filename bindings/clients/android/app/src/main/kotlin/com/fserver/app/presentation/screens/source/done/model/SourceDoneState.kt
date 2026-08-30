package com.fserver.app.presentation.screens.source.done.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.SourceModeUi

/**
 * What was just turned on, in the four lines that answer "what did I do".
 *
 * Every mode ends on the same shape — a mark, one sentence about future behaviour, a summary,
 * two ways out. Offload adds a figure on top, because freeing space is the only reason anyone
 * turns it on.
 */
data class SourceDoneState(
    val kind: SourceKindUi,
    val mode: SourceModeUi,
    val targetName: String = "",
    val summary: List<SummaryRow> = emptyList(),
    /** Offload only: how much comes back, and how many files that covers. */
    val freedLabel: String? = null,
    val freedDetail: String = "",
) {
    @Immutable
    data class SummaryRow(@param:StringRes val labelRes: Int, val value: String)
}
