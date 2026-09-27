package com.fserver.app.presentation.screens.source.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.edit.model.SourceEditIntent
import com.fserver.app.presentation.screens.source.edit.model.SourceEditState
import com.fserver.app.presentation.screens.source.edit.model.SourceEditUiEffect
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.screens.source.shared.preferences.composable.SourcePreferencesForm
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SourceEditScreen(
    modifier: Modifier = Modifier,
    key: Destination.Files.SourceEdit,
    viewModel: SourceEditViewModel = koinViewModel { parametersOf(key) },
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                SourceEditUiEffect.NavigateBack -> navigateUp()
            }
        }
    }

    SourceEditScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SourceEditScreenContent(
    modifier: Modifier = Modifier,
    state: SourceEditState,
    onIntent: (SourceEditIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.source_edit_title),
                subtitle = state.label.takeIf { it.isNotEmpty() },
                onBack = navigateUp,
            )
        },
        bottomBar = {
            DkActionBar {
                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_save),
                    enabled = state.canSave,
                    onClick = { onIntent(SourceEditIntent.Saved) },
                )
            }
        },
    ) { innerPadding ->
        if (state.isLoading) return@DkScaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(top = DkSpacing.lg, bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
        ) {
            if (state.role == SourceRoleUi.Follower) {
                DkInfoBox(
                    modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                    text = stringResource(R.string.source_edit_follower_note, state.peerName),
                )
            }

            SourcePreferencesForm(
                state = state.preferences,
                onIntent = { onIntent(SourceEditIntent.PreferencesChanged(it)) },
                peerName = state.peerName,
                sourceFiles = state.sourceFiles,
                sourceBytes = state.sourceBytes,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceEditScreenPreview() {
    FServerTheme {
        SourceEditScreenContent(
            state = SourceEditState.Sample,
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceEditScreenFollowerPreview() {
    FServerTheme {
        SourceEditScreenContent(
            state = SourceEditState.SampleFollower,
            onIntent = {},
            navigateUp = {},
        )
    }
}
