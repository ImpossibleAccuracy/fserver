package com.fserver.app.presentation.screens.source.setup.conditions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSegmentedControl
import com.fserver.app.presentation.designkit.DkSegmentedOption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkSwitchRow
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.screens.source.setup.conditions.model.EvictCriterionUi
import com.fserver.app.presentation.screens.source.setup.conditions.model.HostRightsUi
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsIntent
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsState
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsUiEffect
import com.fserver.app.presentation.screens.source.setup.conditions.model.UploadScopeUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceChoiceRow
import com.fserver.app.presentation.screens.source.shared.composable.SourceProgressStep
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessFailure
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun SourceConditionsScreen(
    handler: SourceConditionsHandler,
    navigateToProgress: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    val state = handler.state.collectAsStateWithLifecycle().value ?: return

    LaunchedEffect(handler.effects) {
        handler.effects.collect { effect ->
            when (effect) {
                is SourceConditionsUiEffect.NavigateToProgress -> navigateToProgress(effect.sourceId)
            }
        }
    }

    SourceConditionsScreenContent(
        state = state,
        onIntent = handler::onIntent,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SourceConditionsScreenContent(
    state: SourceConditionsState,
    onIntent: (SourceConditionsIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(state.mode.titleRes),
                onBack = navigateUp,
            )
        },
        // Three phases, one place for their controls: a long conditions form must not push
        // "done" out of reach, and the explainer must not bury it under three illustrations.
        bottomBar = {
            DkActionBar {
                when (state.phase) {
                    SourceConditionsState.Phase.Preparing -> DkGhostButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_cancel),
                        onClick = { onIntent(SourceConditionsIntent.PreparingCancelled) },
                    )

                    SourceConditionsState.Phase.Explainer -> {
                        DkPrimaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.offload_explainer_action),
                            onClick = { onIntent(SourceConditionsIntent.ExplainerAccepted) },
                        )
                        DkGhostButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.action_back),
                            onClick = navigateUp,
                        )
                    }

                    SourceConditionsState.Phase.Form -> {
                        DkPrimaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.action_done),
                            onClick = { onIntent(SourceConditionsIntent.Confirmed) },
                        )
                        DkGhostButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.action_back),
                            onClick = navigateUp,
                        )
                    }

                    SourceConditionsState.Phase.Failed -> {
                        DkPrimaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.action_retry),
                            onClick = { onIntent(SourceConditionsIntent.RetryConfirmed) },
                        )
                        DkGhostButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.action_back),
                            onClick = navigateUp,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val bodyModifier = Modifier.padding(innerPadding)

        when (state.phase) {
            SourceConditionsState.Phase.Preparing -> SourceProgressStep(
                modifier = bodyModifier,
                title = stringResource(state.prepareTitleRes),
                body = stringResource(state.prepareBodyRes, state.targetName),
                progress = state.progress,
                detail = state.progressDetail?.asString().orEmpty(),
            )

            SourceConditionsState.Phase.Explainer -> OffloadExplainer(
                modifier = bodyModifier,
                targetName = state.targetName,
            )

            SourceConditionsState.Phase.Form -> ConditionsForm(
                modifier = bodyModifier,
                state = state,
                onIntent = onIntent,
            )

            SourceConditionsState.Phase.Failed -> SourceAccessFailure(
                modifier = bodyModifier,
                title = stringResource(R.string.source_create_failed_title),
                body = state.error?.asString().orEmpty(),
            )
        }
    }
}

@Composable
private fun ConditionsForm(
    modifier: Modifier = Modifier,
    state: SourceConditionsState,
    onIntent: (SourceConditionsIntent) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DkSpacing.screenPadding)
            .padding(bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        Text(
            text = state.headerText(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (state.mode) {
            SourceModeUi.AutoUpload -> AutoUploadFields(state, onIntent)
            SourceModeUi.Offload -> OffloadFields(state, onIntent)
            SourceModeUi.Sync -> SyncFields(state, onIntent)
            SourceModeUi.Host -> HostFields(state, onIntent)
        }
    }
}

@Composable
private fun AutoUploadFields(
    state: SourceConditionsState,
    onIntent: (SourceConditionsIntent) -> Unit,
) {
    DkSectionLabel(text = stringResource(R.string.conditions_upload_scope_label))
    DkSegmentedControl(
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
        selected = state.uploadScope,
        onSelect = { onIntent(SourceConditionsIntent.UploadScopeSelected(it)) },
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.backlogLabel != null) {
        DkCaption(
            text = stringResource(R.string.conditions_upload_scope_hint, state.backlogLabel),
        )
    }

    DkSectionLabel(text = stringResource(R.string.conditions_when_label))
    Column {
        WifiOnlyRow(state, onIntent)
        DkSwitchRow(
            title = stringResource(R.string.conditions_charging_only),
            supportingText = stringResource(R.string.conditions_charging_only_hint),
            checked = state.chargingOnly,
            onCheckedChange = { onIntent(SourceConditionsIntent.ChargingOnlyToggled(it)) },
        )
    }
}

@Composable
private fun OffloadFields(
    state: SourceConditionsState,
    onIntent: (SourceConditionsIntent) -> Unit,
) {
    SourceChoiceRow(
        title = stringResource(R.string.conditions_criterion_age_title),
        description = stringResource(R.string.conditions_criterion_age_hint),
        selected = state.criterion == EvictCriterionUi.OlderThanDays,
        onSelect = {
            onIntent(SourceConditionsIntent.CriterionSelected(EvictCriterionUi.OlderThanDays))
        },
    ) {
        DaysStepper(
            days = state.olderThanDays,
            enabled = state.criterion == EvictCriterionUi.OlderThanDays,
            onStep = { onIntent(SourceConditionsIntent.DaysStepped(it)) },
        )
    }
    SourceChoiceRow(
        title = stringResource(R.string.conditions_criterion_lru_title),
        description = stringResource(R.string.conditions_criterion_lru_hint),
        selected = state.criterion == EvictCriterionUi.LeastRecentlyUsed,
        onSelect = {
            onIntent(SourceConditionsIntent.CriterionSelected(EvictCriterionUi.LeastRecentlyUsed))
        },
    )

    DkSectionLabel(text = stringResource(R.string.conditions_when_label))
    Column {
        WifiOnlyRow(state, onIntent)
        DkSwitchRow(
            title = stringResource(R.string.conditions_charging_only),
            supportingText = stringResource(R.string.conditions_charging_only_hint),
            checked = state.chargingOnly,
            onCheckedChange = { onIntent(SourceConditionsIntent.ChargingOnlyToggled(it)) },
        )
        DkSwitchRow(
            title = stringResource(R.string.conditions_keep_pinned),
            supportingText = stringResource(R.string.conditions_keep_pinned_hint),
            checked = state.keepPinned,
            onCheckedChange = { onIntent(SourceConditionsIntent.KeepPinnedToggled(it)) },
        )
    }
}

@Composable
private fun SyncFields(
    state: SourceConditionsState,
    onIntent: (SourceConditionsIntent) -> Unit,
) {
    // The conflict rule is fixed in the MVP, but it is shown anyway: it decides whether the
    // user loses a version, which is not something to leave implicit.
    DkSectionLabel(text = stringResource(R.string.conditions_sync_conflicts_label))

    Column {
        DkSwitchRow(
            title = stringResource(R.string.conditions_sync_conflicts_rule),
            supportingText = stringResource(R.string.conditions_sync_conflicts_hint),
            checked = true,
            onCheckedChange = {
                // TODO
            }
        )

        DkSwitchRow(
            title = stringResource(R.string.conditions_sync_losers_title),
            supportingText = stringResource(R.string.conditions_sync_losers_hint),
            checked = false,
            onCheckedChange = {
                // TODO
            }
        )
    }

    DkSectionLabel(text = stringResource(R.string.conditions_when_label))
    Column {
        WifiOnlyRow(state, onIntent)
        DkSwitchRow(
            title = stringResource(R.string.conditions_charging_only),
            supportingText = stringResource(R.string.conditions_charging_only_hint),
            checked = state.chargingOnly,
            onCheckedChange = { onIntent(SourceConditionsIntent.ChargingOnlyToggled(it)) },
        )
    }
}

@Composable
private fun HostFields(
    state: SourceConditionsState,
    onIntent: (SourceConditionsIntent) -> Unit,
) {
    DkSectionLabel(text = stringResource(R.string.conditions_host_rights_label))
    SourceChoiceRow(
        title = stringResource(R.string.conditions_host_read_title),
        description = stringResource(R.string.conditions_host_read_hint),
        selected = state.hostRights == HostRightsUi.ReadOnly,
        onSelect = { onIntent(SourceConditionsIntent.HostRightsSelected(HostRightsUi.ReadOnly)) },
    )
    SourceChoiceRow(
        title = stringResource(R.string.conditions_host_write_title),
        description = stringResource(R.string.conditions_host_write_hint),
        selected = state.hostRights == HostRightsUi.ReadWrite,
        onSelect = { onIntent(SourceConditionsIntent.HostRightsSelected(HostRightsUi.ReadWrite)) },
        warning = true,
    )
    // The only mode where this phone becomes a source for others, so the doze caveat sits on
    // the conditions screen rather than in a help page nobody opens.
    DkInfoBox(text = stringResource(R.string.conditions_host_note))
}

@Composable
private fun WifiOnlyRow(
    state: SourceConditionsState,
    onIntent: (SourceConditionsIntent) -> Unit,
) {
    DkSwitchRow(
        title = stringResource(R.string.conditions_wifi_only),
        supportingText = stringResource(R.string.conditions_wifi_only_hint),
        checked = state.wifiOnly,
        onCheckedChange = { onIntent(SourceConditionsIntent.WifiOnlyToggled(it)) },
    )
}

@Composable
private fun DaysStepper(
    modifier: Modifier = Modifier,
    days: Int,
    enabled: Boolean,
    onStep: (Int) -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        DkIconButton(
            icon = Icons.Default.Remove,
            onClick = { if (enabled) onStep(-1) },
        )
        Text(
            modifier = Modifier.widthIn(min = 84.dp),
            text = pluralStringResource(R.plurals.conditions_criterion_age_value, days, days),
            style = DkType.monoLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        DkIconButton(
            icon = Icons.Default.Add,
            onClick = { if (enabled) onStep(1) },
        )
    }
}

/** Offload's own onboarding — three steps, told before anything is deleted. */
@Composable
private fun OffloadExplainer(
    modifier: Modifier = Modifier,
    targetName: String,
) {
    val steps = listOf(
        R.string.offload_explainer_step_copy_title to R.string.offload_explainer_step_copy_body,
        R.string.offload_explainer_step_remove_title to
                R.string.offload_explainer_step_remove_body,
        R.string.offload_explainer_step_keep_title to R.string.offload_explainer_step_keep_body,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DkSpacing.screenPadding)
            .padding(bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
            Text(
                text = stringResource(R.string.offload_explainer_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.offload_explainer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        steps.forEachIndexed { index, (title, body) ->
            Row(horizontalArrangement = Arrangement.spacedBy(DkSpacing.md)) {
                DkPlaceholderBox(
                    label = stringResource(R.string.offload_explainer_illustration, index + 1),
                    modifier = Modifier
                        .width(72.dp)
                        .height(56.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
                ) {
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    DkCaption(text = stringResource(body, targetName))
                }
            }
        }
    }
}

@Composable
private fun SourceConditionsState.headerText(): String = when (mode) {
    SourceModeUi.AutoUpload ->
        stringResource(R.string.conditions_autoupload_body, targetName)

    SourceModeUi.Offload ->
        stringResource(R.string.conditions_offload_body, targetName)

    SourceModeUi.Sync ->
        stringResource(R.string.conditions_sync_body, sourceLabel, targetName)

    SourceModeUi.Host ->
        stringResource(R.string.conditions_host_body)
}

private val SourceConditionsState.prepareTitleRes: Int
    get() = if (mode == SourceModeUi.Offload) {
        R.string.source_prepare_analyse_title
    } else {
        R.string.source_prepare_upload_title
    }

private val SourceConditionsState.prepareBodyRes: Int
    get() = if (mode == SourceModeUi.Offload) {
        R.string.source_prepare_analyse_body
    } else {
        R.string.source_prepare_upload_body
    }

@Preview(showBackground = true)
@Composable
private fun ConditionsAutoUploadPreview() {
    FServerTheme {
        SourceConditionsScreenContent(
            state = SourceConditionsState(
                kind = SourceKindUi.Media,
                mode = SourceModeUi.AutoUpload,
                targetName = "HOME-NAS",
                backlogLabel = "3,402",
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Offload explainer", showBackground = true)
@Composable
private fun ConditionsOffloadExplainerPreview() {
    FServerTheme {
        SourceConditionsScreenContent(
            state = SourceConditionsState(
                kind = SourceKindUi.Media,
                mode = SourceModeUi.Offload,
                phase = SourceConditionsState.Phase.Explainer,
                targetName = "HOME-NAS",
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Offload rule", showBackground = true)
@Composable
private fun ConditionsOffloadPreview() {
    FServerTheme {
        SourceConditionsScreenContent(
            state = SourceConditionsState(
                kind = SourceKindUi.Folder,
                mode = SourceModeUi.Offload,
                targetName = "HOME-NAS",
                sourceLabel = "DCIM/Projects",
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Sync", showBackground = true)
@Composable
private fun ConditionsSyncPreview() {
    FServerTheme {
        SourceConditionsScreenContent(
            state = SourceConditionsState(
                kind = SourceKindUi.Folder,
                mode = SourceModeUi.Sync,
                targetName = "HOME-NAS",
                sourceLabel = "DCIM/Projects",
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Host", showBackground = true)
@Composable
private fun ConditionsHostPreview() {
    FServerTheme {
        SourceConditionsScreenContent(
            state = SourceConditionsState(
                kind = SourceKindUi.Folder,
                mode = SourceModeUi.Host,
                targetName = "HOME-NAS",
                sourceLabel = "DCIM/Projects",
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}
