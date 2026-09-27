package com.fserver.app.presentation.screens.source.request.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkSwitchRow
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.request.preferences.model.SyncRequestPreferencesIntent
import com.fserver.app.presentation.screens.source.request.preferences.model.SyncRequestPreferencesState
import com.fserver.app.presentation.screens.source.request.preferences.model.SyncRequestPreferencesUiEffect
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessFailure
import com.fserver.app.presentation.screens.source.shared.composable.ValueStepper
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SyncRequestPreferencesScreen(
    modifier: Modifier = Modifier,
    key: Destination.Source.Request.Preferences,
    viewModel: SyncRequestPreferencesViewModel = koinViewModel { parametersOf(key) },
    navigateToProgress: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                SyncRequestPreferencesUiEffect.NavigateToProgress -> navigateToProgress()
            }
        }
    }

    SyncRequestPreferencesContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SyncRequestPreferencesContent(
    modifier: Modifier = Modifier,
    state: SyncRequestPreferencesState,
    onIntent: (SyncRequestPreferencesIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.sync_request_preferences_title),
                subtitle = state.request?.label,
                onBack = navigateUp,
            )
        },
        bottomBar = {
            DkActionBar {
                if (state.isGone) {
                    DkPrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_back),
                        onClick = navigateUp,
                    )
                    return@DkActionBar
                }

                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_accept),
                    onClick = { onIntent(SyncRequestPreferencesIntent.Accepted) },
                    enabled = state.canAccept,
                )
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_back),
                    onClick = navigateUp,
                    enabled = !state.isAccepting,
                )
            }
        },
    ) { innerPadding ->
        if (state.isGone) {
            SourceAccessFailure(
                modifier = Modifier.padding(innerPadding),
                title = stringResource(R.string.sync_request_gone_title),
                body = stringResource(R.string.sync_request_gone_body),
            )
            return@DkScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            Text(
                modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                text = stringResource(
                    R.string.sync_request_preferences_body,
                    state.request?.deviceName.orEmpty(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            WhenFields(state = state, onIntent = onIntent)

            LimitsFields(state = state, onIntent = onIntent)

            DkInfoBox(
                modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                text = stringResource(R.string.sync_request_preferences_note),
            )
        }
    }
}

@Composable
private fun WhenFields(
    modifier: Modifier = Modifier,
    state: SyncRequestPreferencesState,
    onIntent: (SyncRequestPreferencesIntent) -> Unit,
) {
    Column(modifier = modifier) {
        DkSectionLabel(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
            text = stringResource(R.string.sync_request_preferences_when_label),
        )
        DkSwitchRow(
            title = stringResource(R.string.conditions_wifi_only),
            supportingText = stringResource(R.string.conditions_wifi_only_hint),
            checked = state.wifiOnly,
            onCheckedChange = { onIntent(SyncRequestPreferencesIntent.WifiOnlyToggled(it)) },
        )
        DkSwitchRow(
            title = stringResource(R.string.conditions_charging_only),
            supportingText = stringResource(R.string.conditions_charging_only_hint),
            checked = state.chargingOnly,
            onCheckedChange = { onIntent(SyncRequestPreferencesIntent.ChargingOnlyToggled(it)) },
        )
    }
}

@Composable
private fun LimitsFields(
    modifier: Modifier = Modifier,
    state: SyncRequestPreferencesState,
    onIntent: (SyncRequestPreferencesIntent) -> Unit,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
        ) {
            DkSectionLabel(text = stringResource(R.string.conditions_limits_label))
            DkCaption(
                text = stringResource(
                    R.string.conditions_limits_hint,
                    state.request?.deviceName.orEmpty(),
                ),
            )
        }
        DkSwitchRow(
            title = stringResource(R.string.conditions_limit_files),
            supportingText = stringResource(R.string.conditions_limit_files_hint),
            checked = state.limitFiles,
            onCheckedChange = { onIntent(SyncRequestPreferencesIntent.LimitFilesToggled(it)) },
        )
        if (state.limitFiles) {
            ValueStepper(
                modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                label = pluralStringResource(
                    R.plurals.conditions_limit_files_value,
                    state.maxFiles,
                    state.maxFiles,
                ),
                enabled = true,
                onStep = { onIntent(SyncRequestPreferencesIntent.MaxFilesStepped(it)) },
            )
        }
        DkSwitchRow(
            title = stringResource(R.string.conditions_limit_size),
            supportingText = stringResource(R.string.conditions_limit_size_hint),
            checked = state.limitSize,
            onCheckedChange = { onIntent(SyncRequestPreferencesIntent.LimitSizeToggled(it)) },
        )
        if (state.limitSize) {
            ValueStepper(
                modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                label = stringResource(R.string.conditions_limit_size_value, state.maxSizeGb),
                enabled = true,
                onStep = { onIntent(SyncRequestPreferencesIntent.MaxSizeStepped(it)) },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestPreferencesPreview() {
    FServerTheme {
        SyncRequestPreferencesContent(
            state = SyncRequestPreferencesState(
                request = SyncRequestUi(
                    sourceId = "3f2a",
                    deviceName = "MacBook-Pro",
                    label = "DCIM/Projects",
                    mode = SourceModeUi.Sync,
                ),
                isLoaded = true,
                limitFiles = true,
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}
