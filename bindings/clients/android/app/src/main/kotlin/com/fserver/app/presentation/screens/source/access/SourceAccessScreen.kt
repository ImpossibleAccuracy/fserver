package com.fserver.app.presentation.screens.source.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.SourceAccessUi
import com.fserver.app.presentation.composable.model.SourceKindUi
import com.fserver.app.presentation.composable.model.titleRes
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCheckState
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkStatusRow
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.access.model.SourceAccessIntent
import com.fserver.app.presentation.screens.source.access.model.SourceAccessState
import com.fserver.app.presentation.screens.source.access.model.SourceAccessUiEffect
import com.fserver.app.presentation.screens.source.composable.SourceAccessFailure
import com.fserver.app.presentation.screens.source.composable.SourceProgressStep
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SourceAccessScreen(
    key: Destination.Source.Access,
    viewModel: SourceAccessViewModel = koinViewModel { parametersOf(key) },
    navigateToMode: (SourceAccessUi) -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is SourceAccessUiEffect.NavigateToMode -> navigateToMode(effect.access)
                // TODO: hand off to Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION once the
                // branch is wired up; the result is read back on resume, not returned here.
                SourceAccessUiEffect.OpenSystemSettings -> Unit
            }
        }
    }

    SourceAccessScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToSourcePick = navigateToSourcePick,
        navigateUp = navigateUp,
    )
}

/**
 * What the branch is about to ask Android for, in the branch's own words.
 *
 * The screen exists because the system dialog cannot be reworded: by the time it appears the
 * user should already know what is being taken and — more usefully — what is not.
 */
@Composable
private fun SourceAccessScreenContent(
    state: SourceAccessState,
    onIntent: (SourceAccessIntent) -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(state.kind.titleRes),
                onBack = navigateUp,
            )
        },
        // Every phase ends in the same two controls in the same place; only their labels change.
        bottomBar = {
            DkActionBar {
                when (state.phase) {
                    SourceAccessState.Phase.Scanning -> DkGhostButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_cancel),
                        onClick = { onIntent(SourceAccessIntent.ScanCancelled) },
                    )

                    SourceAccessState.Phase.Denied -> {
                        DkPrimaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(state.kind.retryRes),
                            onClick = { onIntent(SourceAccessIntent.AccessRequested) },
                        )
                        DkGhostButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.source_error_back_to_sources),
                            onClick = navigateToSourcePick,
                        )
                    }

                    SourceAccessState.Phase.Explaining -> {
                        DkPrimaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(state.kind.continueRes),
                            onClick = { onIntent(SourceAccessIntent.AccessRequested) },
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
            SourceAccessState.Phase.Scanning -> SourceProgressStep(
                modifier = bodyModifier,
                title = stringResource(R.string.source_scan_folder_title),
                body = state.scanPath,
                progress = state.scanProgress,
                detail = state.scanSummary,
            )

            SourceAccessState.Phase.Denied -> SourceAccessFailure(
                modifier = bodyModifier,
                title = stringResource(state.kind.errorTitleRes),
                body = stringResource(state.kind.errorBodyRes),
            )

            SourceAccessState.Phase.Explaining -> AccessExplainer(
                modifier = bodyModifier,
                kind = state.kind,
            )
        }
    }
}

@Composable
private fun AccessExplainer(
    kind: SourceKindUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DkSpacing.screenPadding)
            .padding(bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
    ) {
        kind.illustrationRes?.let { illustration ->
            DkPlaceholderBox(
                label = stringResource(illustration),
                modifier = Modifier.height(160.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
            Text(
                text = stringResource(kind.headingRes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(kind.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The photos branch is the only one that can draw a line between what it takes and what
        // it leaves — for the others the boundary is the folder, or there is none at all.
        if (kind == SourceKindUi.Photos) {
            Column {
                DkStatusRow(
                    title = stringResource(R.string.source_access_photos_visible),
                    detail = stringResource(R.string.source_access_photos_visible_detail),
                    state = DkCheckState.Ok,
                )
                DkStatusRow(
                    title = stringResource(R.string.source_access_photos_hidden),
                    detail = stringResource(R.string.source_access_photos_hidden_detail),
                    state = DkCheckState.Failed,
                )
            }
        }

        kind.noteRes?.let { note ->
            DkInfoBox(text = stringResource(note))
        }
    }
}

private val SourceKindUi.illustrationRes: Int?
    get() = when (this) {
        SourceKindUi.Photos -> R.string.source_access_photos_illustration
        SourceKindUi.Folder -> R.string.source_access_folder_illustration
        // The dangerous branch gets no picture: nothing here should read as an invitation.
        SourceKindUi.WholeDevice -> null
    }

private val SourceKindUi.headingRes: Int
    get() = when (this) {
        SourceKindUi.Photos -> R.string.source_access_photos_heading
        SourceKindUi.Folder -> R.string.source_access_folder_heading
        SourceKindUi.WholeDevice -> R.string.source_access_device_heading
    }

private val SourceKindUi.bodyRes: Int
    get() = when (this) {
        SourceKindUi.Photos -> R.string.source_access_photos_body
        SourceKindUi.Folder -> R.string.source_access_folder_body
        SourceKindUi.WholeDevice -> R.string.source_access_device_body
    }

private val SourceKindUi.noteRes: Int?
    get() = when (this) {
        SourceKindUi.Photos -> null
        SourceKindUi.Folder -> R.string.source_access_folder_note
        SourceKindUi.WholeDevice -> R.string.source_access_device_note
    }

private val SourceKindUi.continueRes: Int
    get() = when (this) {
        SourceKindUi.Photos -> R.string.action_continue
        SourceKindUi.Folder -> R.string.source_access_folder_action
        SourceKindUi.WholeDevice -> R.string.source_access_device_action
    }

private val SourceKindUi.errorTitleRes: Int
    get() = when (this) {
        SourceKindUi.Photos -> R.string.source_error_photos_title
        SourceKindUi.Folder -> R.string.source_error_folder_title
        SourceKindUi.WholeDevice -> R.string.source_error_device_title
    }

private val SourceKindUi.errorBodyRes: Int
    get() = when (this) {
        SourceKindUi.Photos -> R.string.source_error_photos_body
        SourceKindUi.Folder -> R.string.source_error_folder_body
        SourceKindUi.WholeDevice -> R.string.source_error_device_body
    }

private val SourceKindUi.retryRes: Int
    get() = when (this) {
        SourceKindUi.Photos,
        SourceKindUi.Folder -> R.string.action_retry
        SourceKindUi.WholeDevice -> R.string.source_error_open_settings
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceAccessPhotosPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(kind = SourceKindUi.Photos),
            onIntent = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Folder", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceAccessFolderPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(kind = SourceKindUi.Folder),
            onIntent = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Whole device", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceAccessDevicePreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(kind = SourceKindUi.WholeDevice),
            onIntent = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Denied", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceAccessDeniedPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(
                kind = SourceKindUi.Photos,
                phase = SourceAccessState.Phase.Denied,
            ),
            onIntent = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Scanning", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceAccessScanningPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(
                kind = SourceKindUi.Folder,
                phase = SourceAccessState.Phase.Scanning,
                scanProgress = 0.4f,
                scanPath = SourceAccessViewModel.SampleFolderPath,
                scanSummary = SourceAccessViewModel.SampleFolderSummary,
            ),
            onIntent = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}
