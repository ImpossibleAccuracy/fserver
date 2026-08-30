package com.fserver.app.presentation.screens.source.done.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.fserver.app.R
import com.fserver.app.presentation.screens.source.shared.composable.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.SourceSummaryUi

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

    companion object {
        private const val SampleTarget = "HOME-NAS"

        /**
         * The summary is the one screen the flow's ViewModel does not feed: by the time it is
         * shown the flow has been dropped from the back stack, so everything it reports comes
         * out of [summary].
         */
        fun of(summary: SourceSummaryUi): SourceDoneState = when (summary.mode) {
            SourceModeUi.AutoUpload -> SourceDoneState(
                kind = summary.kind,
                mode = summary.mode,
                targetName = SampleTarget,
                summary = listOf(
                    SummaryRow(R.string.source_summary_source, summary.sourceLabel),
                    SummaryRow(R.string.source_summary_target, SampleTarget),
                    SummaryRow(R.string.source_summary_conditions, "New · Wi-Fi only"),
                    SummaryRow(R.string.source_summary_queued, "12 files · 240 MB"),
                ),
            )

            SourceModeUi.Offload -> SourceDoneState(
                kind = summary.kind,
                mode = summary.mode,
                targetName = SampleTarget,
                freedLabel = "~18.4 GB",
                freedDetail = "2,140 files",
                summary = listOf(
                    SummaryRow(R.string.source_summary_source, summary.sourceLabel),
                    SummaryRow(
                        R.string.source_summary_rule,
                        "Older than ${summary.olderThanDays} days",
                    ),
                ),
            )

            SourceModeUi.Sync -> SourceDoneState(
                kind = summary.kind,
                mode = summary.mode,
                targetName = SampleTarget,
                summary = listOf(
                    SummaryRow(R.string.source_summary_folder, summary.sourceLabel),
                    SummaryRow(R.string.source_summary_peer, SampleTarget),
                    SummaryRow(R.string.source_summary_contents, "${summary.files} files"),
                ),
            )

            SourceModeUi.Host -> SourceDoneState(
                kind = summary.kind,
                mode = summary.mode,
                targetName = SampleTarget,
                summary = listOf(
                    SummaryRow(R.string.source_summary_folder, summary.sourceLabel),
                    SummaryRow(R.string.source_summary_rights, "Read only"),
                    SummaryRow(R.string.source_summary_address, "192.168.1.37:8384"),
                ),
            )
        }
    }
}
