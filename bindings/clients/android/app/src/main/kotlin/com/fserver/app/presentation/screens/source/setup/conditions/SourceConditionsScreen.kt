package com.fserver.app.presentation.screens.source.setup.conditions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
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
import com.fserver.app.presentation.screens.source.shared.preferences.model.EvictCriterionUi
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsIntent
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsState
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsUiEffect
import com.fserver.app.presentation.screens.source.shared.preferences.model.UploadScopeUi
import com.fserver.app.presentation.screens.source.shared.preferences.composable.SourcePreferencesForm
import com.fserver.app.presentation.screens.source.shared.composable.SourceProgressStep
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
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
            .padding(bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
            text = state.headerText(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.mode == SourceModeUi.Host) {
            DkInfoBox(
                modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                text = stringResource(R.string.conditions_host_note, state.targetName),
            )
        }

        SourcePreferencesForm(
            state = state.preferences,
            onIntent = { onIntent(SourceConditionsIntent.PreferencesChanged(it)) },
            peerName = state.targetName,
            sourceFiles = state.sourceFiles,
            sourceBytes = state.sourceBytes,
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
        stringResource(R.string.conditions_host_body, targetName)
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
                preferences = SourcePreferencesUi.build(SourceModeUi.AutoUpload, SourceRoleUi.Initiator),
                sourceFiles = 3402,
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
                preferences = SourcePreferencesUi.build(SourceModeUi.Offload, SourceRoleUi.Initiator),
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
                preferences = SourcePreferencesUi.build(SourceModeUi.Sync, SourceRoleUi.Initiator),
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
