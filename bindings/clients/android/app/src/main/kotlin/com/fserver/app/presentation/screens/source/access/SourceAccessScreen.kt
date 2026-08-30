package com.fserver.app.presentation.screens.source.access

import android.text.format.Formatter
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
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
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessFailure
import com.fserver.app.presentation.screens.source.shared.composable.SourceProgressStep
import com.fserver.app.presentation.screens.source.shared.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.rememberSourceAccessRequester
import com.fserver.app.presentation.screens.source.shared.titleRes
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

    val requester = rememberSourceAccessRequester { grant ->
        viewModel.onIntent(SourceAccessIntent.AccessAnswered(grant))
    }

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is SourceAccessUiEffect.NavigateToMode -> navigateToMode(effect.access)
            }
        }
    }

    SourceAccessScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        onRequestAccess = { requester.request(state.kind) },
        navigateToSourcePick = navigateToSourcePick,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SourceAccessScreenContent(
    state: SourceAccessState,
    onIntent: (SourceAccessIntent) -> Unit,
    onRequestAccess: () -> Unit,
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
                            onClick = onRequestAccess,
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
                            onClick = onRequestAccess,
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
                progress = null,
                detail = stringResource(
                    R.string.source_scan_folder_summary,
                    state.scannedFiles,
                    Formatter.formatShortFileSize(LocalContext.current, state.scannedBytes),
                ),
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

@Preview(showBackground = true)
@Composable
private fun SourceAccessPhotosPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(kind = SourceKindUi.Photos),
            onIntent = {},
            onRequestAccess = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Folder", showBackground = true)
@Composable
private fun SourceAccessFolderPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(kind = SourceKindUi.Folder),
            onIntent = {},
            onRequestAccess = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Whole device", showBackground = true)
@Composable
private fun SourceAccessDevicePreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(kind = SourceKindUi.WholeDevice),
            onIntent = {},
            onRequestAccess = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Denied", showBackground = true)
@Composable
private fun SourceAccessDeniedPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(
                kind = SourceKindUi.Photos,
                phase = SourceAccessState.Phase.Denied,
            ),
            onIntent = {},
            onRequestAccess = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Scanning", showBackground = true)
@Composable
private fun SourceAccessScanningPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(
                kind = SourceKindUi.Folder,
                phase = SourceAccessState.Phase.Scanning,
                scanPath = "/DCIM/Projects",
                scannedFiles = 842,
                scannedBytes = 6_549_123_072L,
            ),
            onIntent = {},
            onRequestAccess = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}
