package com.fserver.app.presentation.screens.source.done.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.fserver.app.R
import com.fserver.app.presentation.screens.source.shared.model.PickedSourceUi
import com.fserver.app.presentation.screens.source.shared.model.SourceFlowState
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.core.network.device.model.ForeignDevice

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
        /**
         * The summary is the one screen the flow's ViewModel does not feed: by the time it is
         * shown the flow has been dropped from the back stack, so everything it reports comes
         * out of [summary].
         */
        fun of(
            kind: SourceKindUi,
            mode: SourceModeUi,
            source: PickedSourceUi,
            conditions: SourceFlowState.SavedConditions,
            target: ForeignDevice?,
        ): SourceDoneState {
            val targetName = target?.displayName ?: ""

            val summary = buildList {
                add(SummaryRow(R.string.source_summary_source, source.label))
                if (mode != SourceModeUi.Host) {
                    add(SummaryRow(R.string.source_summary_target, targetName))
                }

                when (mode) {
                    SourceModeUi.Sync -> {
                        add(SummaryRow(R.string.source_summary_contents, "${source.files} files"))
                    }

                    SourceModeUi.AutoUpload -> {
                        add(SummaryRow(R.string.source_summary_conditions, "New · Wi-Fi only"))
                        add(SummaryRow(R.string.source_summary_queued, "12 files · 240 MB"))
                    }

                    SourceModeUi.Offload -> {
                        add(
                            SummaryRow(
                                R.string.source_summary_rule,
                                "Older than ${conditions.olderThanDays} days",
                            )
                        )
                    }

                    SourceModeUi.Host -> {
                        add(SummaryRow(R.string.source_summary_rights, "Read only"))
                        add(SummaryRow(R.string.source_summary_address, "192.168.1.37:8384"))
                    }
                }
            }

            return SourceDoneState(
                kind = kind,
                mode = mode,
                targetName = targetName,
                summary = summary,
            )
        }
    }
}
