package com.fserver.app.presentation.screens.source.shared.preferences.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkChoiceBar
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkProgressBar
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSegmentedOption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkSwitchRow
import com.fserver.app.presentation.screens.source.shared.composable.SourceChoiceRow
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.ConflictResolutionUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.EvictCriterionUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesIntent
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.UploadScopeUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

/**
 * Every setting of one side of a source, one group per non-null section of [state] — so the same
 * form serves creating, accepting and editing, whatever the mode and role.
 *
 * Rows are full-bleed, so place it in an unguttered column. [peerName] names the other side in the
 * hints; [sourceFiles] and [sourceBytes], when known, size the backlog and the size limit.
 */
@Composable
fun SourcePreferencesForm(
    modifier: Modifier = Modifier,
    state: SourcePreferencesUi,
    onIntent: (SourcePreferencesIntent) -> Unit,
    peerName: String,
    sourceFiles: Int? = null,
    sourceBytes: Long? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
    ) {
        state.upload?.let { upload ->
            UploadFields(upload = upload, onIntent = onIntent, sourceFiles = sourceFiles)
        }
        state.eviction?.let { eviction ->
            EvictionFields(eviction = eviction, onIntent = onIntent)
        }
        state.conflicts?.let { conflicts ->
            ConflictsFields(conflicts = conflicts, onIntent = onIntent)
        }

        ConstraintsFields(state = state, onIntent = onIntent)

        state.limits?.let { limits ->
            LimitsFields(
                limits = limits,
                onIntent = onIntent,
                peerName = peerName,
                sourceBytes = sourceBytes,
            )
        }
    }
}

@Composable
private fun UploadFields(
    modifier: Modifier = Modifier,
    upload: SourcePreferencesUi.UploadUi,
    onIntent: (SourcePreferencesIntent) -> Unit,
    sourceFiles: Int?,
) {
    Column(
        modifier = modifier.padding(horizontal = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        DkSectionLabel(text = stringResource(R.string.conditions_upload_scope_label))
        DkChoiceBar(
            options = listOf(
                DkSegmentedOption(
                    value = UploadScopeUi.New,
                    label = stringResource(R.string.conditions_upload_new),
                ),
                DkSegmentedOption(
                    value = UploadScopeUi.All,
                    label = stringResource(R.string.conditions_upload_all),
                ),
            ),
            selected = upload.scope,
            onSelect = { onIntent(SourcePreferencesIntent.UploadScopeSelected(it)) },
        )
        if (sourceFiles != null && upload.scope == UploadScopeUi.All) {
            DkCaption(
                text = stringResource(R.string.conditions_upload_scope_hint, sourceFiles.toString()),
            )
        }
    }
}

@Composable
private fun EvictionFields(
    modifier: Modifier = Modifier,
    eviction: SourcePreferencesUi.EvictionUi,
    onIntent: (SourcePreferencesIntent) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            SourceChoiceRow(
                title = stringResource(R.string.conditions_criterion_age_title),
                description = stringResource(R.string.conditions_criterion_age_hint),
                selected = eviction.criterion == EvictCriterionUi.OlderThanDays,
                onSelect = {
                    onIntent(SourcePreferencesIntent.CriterionSelected(EvictCriterionUi.OlderThanDays))
                },
            ) {
                ValueStepper(
                    label = pluralStringResource(
                        R.plurals.conditions_criterion_age_value,
                        eviction.olderThanDays,
                        eviction.olderThanDays,
                    ),
                    enabled = eviction.criterion == EvictCriterionUi.OlderThanDays,
                    onStep = { onIntent(SourcePreferencesIntent.DaysStepped(it)) },
                )
            }
            SourceChoiceRow(
                title = stringResource(R.string.conditions_criterion_size_title),
                description = stringResource(R.string.conditions_criterion_size_hint),
                selected = eviction.criterion == EvictCriterionUi.LargerThan,
                onSelect = {
                    onIntent(SourcePreferencesIntent.CriterionSelected(EvictCriterionUi.LargerThan))
                },
            ) {
                ValueStepper(
                    label = stringResource(
                        R.string.conditions_criterion_size_value,
                        FileSize(eviction.largerThanBytes).formatted(),
                    ),
                    enabled = eviction.criterion == EvictCriterionUi.LargerThan,
                    onStep = { onIntent(SourcePreferencesIntent.SizeThresholdStepped(it)) },
                )
            }
            DkCaption(text = stringResource(R.string.conditions_keep_pinned_hint))
        }
    }
}

@Composable
private fun ConflictsFields(
    modifier: Modifier = Modifier,
    conflicts: SourcePreferencesUi.ConflictsUi,
    onIntent: (SourcePreferencesIntent) -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        DkSectionLabel(text = stringResource(R.string.conditions_sync_conflicts_label))
        SourceChoiceRow(
            title = stringResource(R.string.conditions_sync_conflicts_ask),
            description = stringResource(R.string.conditions_sync_conflicts_ask_hint),
            selected = conflicts.resolution == ConflictResolutionUi.Ask,
            onSelect = {
                onIntent(SourcePreferencesIntent.ConflictResolutionSelected(ConflictResolutionUi.Ask))
            },
        )
        SourceChoiceRow(
            title = stringResource(R.string.conditions_sync_conflicts_rule),
            description = stringResource(R.string.conditions_sync_conflicts_hint),
            selected = conflicts.resolution == ConflictResolutionUi.LastWriteWins,
            onSelect = {
                onIntent(
                    SourcePreferencesIntent.ConflictResolutionSelected(ConflictResolutionUi.LastWriteWins)
                )
            },
        )
    }
}

@Composable
private fun ConstraintsFields(
    modifier: Modifier = Modifier,
    state: SourcePreferencesUi,
    onIntent: (SourcePreferencesIntent) -> Unit,
) {
    Column(modifier = modifier) {
        SectionHeader(label = stringResource(R.string.source_preferences_when_label))
        DkSwitchRow(
            title = stringResource(R.string.conditions_wifi_only),
            supportingText = stringResource(R.string.conditions_wifi_only_hint),
            leadingIcon = Icons.Default.Wifi,
            checked = state.wifiOnly,
            onCheckedChange = { onIntent(SourcePreferencesIntent.WifiOnlyToggled(it)) },
        )
        DkSwitchRow(
            title = stringResource(R.string.conditions_charging_only),
            supportingText = stringResource(R.string.conditions_charging_only_hint),
            leadingIcon = Icons.Default.Bolt,
            checked = state.chargingOnly,
            onCheckedChange = { onIntent(SourcePreferencesIntent.ChargingOnlyToggled(it)) },
        )
    }
}

@Composable
private fun LimitsFields(
    modifier: Modifier = Modifier,
    limits: SourcePreferencesUi.LimitsUi,
    onIntent: (SourcePreferencesIntent) -> Unit,
    peerName: String,
    sourceBytes: Long?,
) {
    Column(modifier = modifier) {
        SectionHeader(
            label = stringResource(R.string.conditions_limits_label),
            hint = stringResource(R.string.conditions_limits_hint, peerName),
        )
        DkSwitchRow(
            title = stringResource(R.string.conditions_limit_files),
            supportingText = stringResource(R.string.conditions_limit_files_hint),
            leadingIcon = Icons.Default.Description,
            checked = limits.limitFiles,
            onCheckedChange = { onIntent(SourcePreferencesIntent.LimitFilesToggled(it)) },
        )
        if (limits.limitFiles) {
            ValueStepper(
                modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                label = pluralStringResource(
                    R.plurals.conditions_limit_files_value,
                    limits.maxFiles,
                    limits.maxFiles,
                ),
                enabled = true,
                onStep = { onIntent(SourcePreferencesIntent.MaxFilesStepped(it)) },
            )
        }
        DkSwitchRow(
            title = stringResource(R.string.conditions_limit_size),
            supportingText = stringResource(R.string.conditions_limit_size_hint),
            leadingIcon = Icons.Default.Folder,
            checked = limits.limitSize,
            onCheckedChange = { onIntent(SourcePreferencesIntent.LimitSizeToggled(it)) },
        )
        if (limits.limitSize) {
            SizeLimitFields(
                modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                limits = limits,
                onIntent = onIntent,
                sourceBytes = sourceBytes,
            )
        }
    }
}

@Composable
private fun SizeLimitFields(
    modifier: Modifier = Modifier,
    limits: SourcePreferencesUi.LimitsUi,
    onIntent: (SourcePreferencesIntent) -> Unit,
    sourceBytes: Long?,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        DkChoiceBar(
            options = SourcePreferencesUi.SizePresetsGb.map { gb ->
                DkSegmentedOption<Int?>(
                    value = gb,
                    label = stringResource(R.string.conditions_limit_size_value, gb),
                )
            } + DkSegmentedOption<Int?>(
                value = null,
                label = stringResource(R.string.source_preferences_size_custom),
            ),
            selected = limits.sizePresetGb,
            onSelect = { onIntent(SourcePreferencesIntent.SizePresetSelected(it)) },
        )

        if (limits.sizePresetGb == null) {
            ValueStepper(
                label = stringResource(R.string.conditions_limit_size_value, limits.maxSizeGb),
                enabled = true,
                onStep = { onIntent(SourcePreferencesIntent.MaxSizeStepped(it)) },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            DkProgressBar(
                modifier = Modifier.weight(1f),
                progress = sourceBytes
                    ?.let { (it.toFloat() / limits.maxSizeBytes).coerceIn(0f, 1f) }
                    ?: 0f,
            )
            DkMonoCaption(
                text = stringResource(
                    R.string.source_preferences_size_usage,
                    sourceBytes?.let { FileSize(it).formatted() }
                        ?: stringResource(R.string.source_preferences_size_unknown),
                    FileSize(limits.maxSizeBytes).formatted(),
                ),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    modifier: Modifier = Modifier,
    label: String,
    hint: String? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
    ) {
        DkSectionLabel(text = label)
        if (hint != null) {
            DkCaption(text = hint)
        }
    }
}

@Preview(name = "Follower", showBackground = true, widthDp = 360)
@Composable
private fun SourcePreferencesFormFollowerPreview() {
    FServerTheme {
        SourcePreferencesForm(
            state = SourcePreferencesUi.build(SourceModeUi.Sync, SourceRoleUi.Follower).let {
                it.copy(limits = it.limits?.copy(limitSize = true))
            },
            onIntent = {},
            peerName = "MacBook-Pro",
            sourceBytes = 480L * 1024 * 1024,
        )
    }
}

@Preview(name = "Initiator, sync", showBackground = true, widthDp = 360)
@Composable
private fun SourcePreferencesFormInitiatorPreview() {
    FServerTheme {
        SourcePreferencesForm(
            state = SourcePreferencesUi.build(SourceModeUi.Sync, SourceRoleUi.Initiator),
            onIntent = {},
            peerName = "HOME-NAS",
        )
    }
}

@Preview(name = "Initiator, offload", showBackground = true, widthDp = 360)
@Composable
private fun SourcePreferencesFormOffloadPreview() {
    FServerTheme {
        SourcePreferencesForm(
            state = SourcePreferencesUi.build(SourceModeUi.Offload, SourceRoleUi.Initiator),
            onIntent = {},
            peerName = "HOME-NAS",
        )
    }
}
