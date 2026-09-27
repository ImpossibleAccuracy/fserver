package com.fserver.app.presentation.screens.source.shared.done.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.fserver.app.R
import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.screens.source.shared.model.nameOf
import com.fserver.app.presentation.screens.source.shared.model.readablePath
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode

@Immutable
data class SourceDoneState(
    val role: SourceRoleUi = SourceRoleUi.Initiator,
    val mode: SourceModeUi = SourceModeUi.Sync,
    val peerName: String = "",
    val summary: List<SummaryRow> = emptyList(),
) {
    val canAddAnother: Boolean get() = role == SourceRoleUi.Initiator

    @get:StringRes
    val titleRes: Int
        get() = when (role) {
            SourceRoleUi.Follower -> R.string.source_done_incoming_title
            SourceRoleUi.Initiator -> when (mode) {
                SourceModeUi.AutoUpload -> R.string.source_done_autoupload_title
                SourceModeUi.Offload -> R.string.source_done_offload_title
                SourceModeUi.Sync -> R.string.source_done_sync_title
                SourceModeUi.Host -> R.string.source_done_host_title
            }
        }

    @Immutable
    data class SummaryRow(@param:StringRes val labelRes: Int, val value: UiText)

    companion object {
        fun of(entry: SourceEntry, devices: List<TrustedDevice>): SourceDoneState {
            val role = entry.role.toUi()
            val mode = entry.syncMode.toUi()
            val peerName = devices.nameOf(entry.deviceId)

            val summary = buildList {
                add(SummaryRow(R.string.source_summary_source, UiText.Text(entry.label)))
                add(SummaryRow(role.peerLabelRes, UiText.Text(peerName)))
                add(SummaryRow(R.string.source_summary_mode, UiText.Resource(mode.titleRes)))
                entry.location.value()?.let {
                    add(SummaryRow(role.locationLabelRes, it))
                }
                addAll(entry.syncMode.conditionRows())
            }

            return SourceDoneState(
                role = role,
                mode = mode,
                peerName = peerName,
                summary = summary,
            )
        }

        @get:StringRes
        private val SourceRoleUi.peerLabelRes: Int
            get() = when (this) {
                SourceRoleUi.Initiator -> R.string.source_summary_target
                SourceRoleUi.Follower -> R.string.source_summary_peer
            }

        @get:StringRes
        private val SourceRoleUi.locationLabelRes: Int
            get() = when (this) {
                SourceRoleUi.Initiator -> R.string.source_summary_sent_from
                SourceRoleUi.Follower -> R.string.source_summary_saved_to
            }

        private fun SourceLocation.value(): UiText? = when (this) {
            is SourceLocation.Internal ->
                UiText.Resource(R.string.sync_request_location_internal_title)

            SourceLocation.Media -> UiText.Resource(R.string.source_summary_location_media)
            is SourceLocation.Tree,
            is SourceLocation.Directory -> readablePath()?.let(UiText::Text)

            is SourceLocation.Root -> null
        }

        private fun SyncMode.conditionRows(): List<SummaryRow> = when (this) {
            is SyncMode.Mirror -> emptyList()

            is SyncMode.AutoUpload -> listOf(
                SummaryRow(
                    R.string.source_summary_conditions,
                    UiText.Resource(
                        if (ignoreFilesBefore == null) R.string.source_summary_scope_all
                        else R.string.source_summary_scope_new
                    ),
                )
            )

            is SyncMode.Offload -> listOf(
                SummaryRow(R.string.source_summary_rule, policy.ruleValue()),
                SummaryRow(R.string.source_summary_pinned, UiText.Resource(R.string.source_summary_pinned_kept)),
            )

            SyncMode.Host -> listOf(
                SummaryRow(R.string.source_summary_rule, UiText.Resource(R.string.source_summary_rule_host)),
            )
        }

        private fun SyncMode.Offload.EvictPolicy.ruleValue(): UiText = when (this) {
            is SyncMode.Offload.EvictPolicy.OlderThanDays ->
                UiText.Resource(R.string.source_summary_rule_older, listOf(days))

            is SyncMode.Offload.EvictPolicy.LargerThanBytes ->
                UiText.Size(R.string.source_summary_rule_larger, bytes)
        }
    }
}
