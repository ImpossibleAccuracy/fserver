package com.fserver.app.presentation.screens.source.done

import androidx.lifecycle.ViewModel
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.SourceKindUi
import com.fserver.app.presentation.composable.model.SourceModeUi
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.done.model.SourceDoneState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TODO: every value below is a fixture. The summary is the one place the flow reports what it
 * committed, so it reads from the configured source once `:core` stores one.
 */
class SourceDoneViewModel(
    key: Destination.Source.Done,
) : ViewModel() {

    private val _state = MutableStateFlow(buildState(key))
    val state: StateFlow<SourceDoneState> = _state.asStateFlow()

    private companion object {
        const val SampleTarget = "HOME-NAS"
        const val SampleFolder = "DCIM/Projects"

        fun buildState(key: Destination.Source.Done): SourceDoneState {
            val sourceLabel = when (key.kind) {
                SourceKindUi.Photos -> "Photos and videos"
                SourceKindUi.Folder -> SampleFolder
                SourceKindUi.WholeDevice -> "Whole device"
            }

            return when (key.mode) {
                SourceModeUi.AutoUpload -> SourceDoneState(
                    kind = key.kind,
                    mode = key.mode,
                    targetName = SampleTarget,
                    summary = listOf(
                        SourceDoneState.SummaryRow(R.string.source_summary_source, sourceLabel),
                        SourceDoneState.SummaryRow(R.string.source_summary_target, SampleTarget),
                        SourceDoneState.SummaryRow(
                            R.string.source_summary_conditions,
                            "New · Wi-Fi only",
                        ),
                        SourceDoneState.SummaryRow(
                            R.string.source_summary_queued,
                            "12 files · 240 MB",
                        ),
                    ),
                )

                SourceModeUi.Offload -> SourceDoneState(
                    kind = key.kind,
                    mode = key.mode,
                    targetName = SampleTarget,
                    freedLabel = "~18.4 GB",
                    freedDetail = "2,140 files",
                    summary = listOf(
                        SourceDoneState.SummaryRow(R.string.source_summary_source, sourceLabel),
                        SourceDoneState.SummaryRow(
                            R.string.source_summary_rule,
                            "Older than 60 days",
                        ),
                    ),
                )

                SourceModeUi.Sync -> SourceDoneState(
                    kind = key.kind,
                    mode = key.mode,
                    targetName = SampleTarget,
                    summary = listOf(
                        SourceDoneState.SummaryRow(R.string.source_summary_folder, sourceLabel),
                        SourceDoneState.SummaryRow(R.string.source_summary_peer, SampleTarget),
                        SourceDoneState.SummaryRow(
                            R.string.source_summary_contents,
                            "842 files · 6.1 GB",
                        ),
                    ),
                )

                SourceModeUi.Host -> SourceDoneState(
                    kind = key.kind,
                    mode = key.mode,
                    targetName = SampleTarget,
                    summary = listOf(
                        SourceDoneState.SummaryRow(R.string.source_summary_folder, sourceLabel),
                        SourceDoneState.SummaryRow(R.string.source_summary_rights, "Read only"),
                        // The address is what makes the host summary different: it is the thing
                        // the user reads aloud to the second device.
                        SourceDoneState.SummaryRow(
                            R.string.source_summary_address,
                            "192.168.1.37:8384",
                        ),
                    ),
                )
            }
        }
    }
}
