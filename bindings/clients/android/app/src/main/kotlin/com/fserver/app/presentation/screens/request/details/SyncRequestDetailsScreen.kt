package com.fserver.app.presentation.screens.request.details

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.LocalSnackbarController
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkValueRow
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.request.details.model.SyncRequestDetailsIntent
import com.fserver.app.presentation.screens.request.details.model.SyncRequestDetailsState
import com.fserver.app.presentation.screens.request.details.model.SyncRequestDetailsUiEffect
import com.fserver.app.presentation.screens.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessFailure
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SyncRequestDetailsScreen(
    modifier: Modifier = Modifier,
    key: Destination.SyncRequest.Details,
    viewModel: SyncRequestDetailsViewModel = koinViewModel { parametersOf(key) },
    navigateToLocation: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarController.current

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                SyncRequestDetailsUiEffect.NavigateBack -> navigateUp()
                is SyncRequestDetailsUiEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            }
        }
    }

    SyncRequestDetailsContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToLocation = navigateToLocation,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SyncRequestDetailsContent(
    modifier: Modifier = Modifier,
    state: SyncRequestDetailsState,
    onIntent: (SyncRequestDetailsIntent) -> Unit,
    navigateToLocation: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.sync_request_title),
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
                    text = stringResource(R.string.action_continue),
                    onClick = navigateToLocation,
                    enabled = state.canAnswer,
                )
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_decline),
                    onClick = { onIntent(SyncRequestDetailsIntent.Declined) },
                    enabled = state.canAnswer,
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

        val request = state.request ?: return@DkScaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DkSpacing.screenPadding)
                .padding(bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.sync_request_heading, request.deviceName),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.sync_request_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DkCard {
                DkValueRow(
                    title = stringResource(R.string.sync_request_from),
                    value = request.deviceName,
                )
                DkValueRow(
                    title = stringResource(R.string.sync_request_source),
                    value = request.label,
                )
                DkValueRow(
                    title = stringResource(R.string.sync_request_mode),
                    value = stringResource(request.mode.titleRes),
                )
            }

            DkInfoBox(text = stringResource(R.string.sync_request_note))
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestDetailsPreview() {
    FServerTheme {
        SyncRequestDetailsContent(
            state = SyncRequestDetailsState(
                request = SyncRequestUi(
                    sourceId = "3f2a",
                    deviceName = "MacBook-Pro",
                    label = "DCIM/Projects",
                    mode = SourceModeUi.Sync,
                ),
                isLoaded = true,
            ),
            onIntent = {},
            navigateToLocation = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Already answered", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestDetailsGonePreview() {
    FServerTheme {
        SyncRequestDetailsContent(
            state = SyncRequestDetailsState(isLoaded = true),
            onIntent = {},
            navigateToLocation = {},
            navigateUp = {},
        )
    }
}
