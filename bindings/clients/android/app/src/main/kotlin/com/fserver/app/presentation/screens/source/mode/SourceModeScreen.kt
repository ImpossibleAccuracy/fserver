package com.fserver.app.presentation.screens.source.mode

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.source.mode.model.SourceModeIntent
import com.fserver.app.presentation.screens.source.mode.model.SourceModeState
import com.fserver.app.presentation.screens.source.shared.SourceFlowViewModel
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceChoiceRow
import com.fserver.app.presentation.screens.source.shared.composable.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.composable.modeTitleRes
import com.fserver.app.presentation.screens.source.shared.composable.titleRes
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun SourceModeScreen(
    viewModel: SourceFlowViewModel,
    navigateNext: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state = viewModel.modeState.collectAsStateWithLifecycle().value ?: return

    SourceModeScreenContent(
        state = state,
        onIntent = viewModel::onModeIntent,
        navigateNext = navigateNext,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SourceModeScreenContent(
    state: SourceModeState,
    onIntent: (SourceModeIntent) -> Unit,
    navigateNext: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(state.kind.modeTitleRes),
                onBack = navigateUp,
            )
        },
        bottomBar = {
            DkActionBar {
                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_continue),
                    enabled = state.canContinue,
                    onClick = navigateNext,
                )
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_back),
                    onClick = navigateUp,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DkSpacing.screenPadding)
                .padding(bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            if (state.isPartial) {
                PartialAccessNotice(
                    count = state.grantedItemCount,
                    onChangeSelection = { onIntent(SourceModeIntent.ChangeSelectionClicked) },
                )
                DkCaption(
                    text = pluralStringResource(
                        R.plurals.source_mode_partial_hint,
                        state.grantedItemCount,
                        state.grantedItemCount,
                    )
                )
            } else if (state.sourceLabel.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs)) {
                    Text(
                        text = state.sourceLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (state.sourceFiles > 0) {
                        DkMonoCaption(
                            text = stringResource(
                                R.string.source_scan_folder_summary,
                                state.sourceFiles,
                                Formatter.formatShortFileSize(
                                    LocalContext.current,
                                    state.sourceBytes,
                                ),
                            )
                        )
                    }
                }
            } else {
                DkCaption(text = stringResource(R.string.source_mode_hint))
            }

            state.modes.forEach { mode ->
                SourceChoiceRow(
                    title = stringResource(mode.titleRes),
                    description = stringResource(mode.subtitleRes(state.kind)),
                    selected = mode == state.selected,
                    onSelect = { onIntent(SourceModeIntent.ModeSelected(mode)) },
                    recommended = mode == state.modes.first() && state.kind == SourceKindUi.Media,
                )
            }
        }
    }
}

/** A partial grant lives at the top of the screen, not in a toast that scrolls away. */
@Composable
private fun PartialAccessNotice(
    count: Int,
    onChangeSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DkCard(modifier = modifier, outlined = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = pluralStringResource(R.plurals.source_mode_partial_title, count, count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                modifier = Modifier
                    .padding(start = DkSpacing.sm)
                    .clickable(onClick = onChangeSelection),
                text = stringResource(R.string.source_mode_partial_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The mode's one sentence about what happens to the original, worded per branch. */
private fun SourceModeUi.subtitleRes(kind: SourceKindUi): Int = when (this) {
    SourceModeUi.AutoUpload -> if (kind == SourceKindUi.Media) {
        R.string.mode_autoupload_photos_subtitle
    } else {
        R.string.mode_autoupload_subtitle
    }

    SourceModeUi.Offload -> if (kind == SourceKindUi.Media) {
        R.string.mode_offload_photos_subtitle
    } else {
        R.string.mode_offload_subtitle
    }

    SourceModeUi.Sync -> R.string.mode_sync_subtitle
    SourceModeUi.Host -> R.string.mode_host_subtitle
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceModePhotosPreview() {
    FServerTheme {
        SourceModeScreenContent(
            state = SourceModeState(
                kind = SourceKindUi.Media,
                selected = SourceModeUi.AutoUpload,
            ),
            onIntent = {},
            navigateNext = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Partial access", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceModePartialPreview() {
    FServerTheme {
        SourceModeScreenContent(
            state = SourceModeState(
                kind = SourceKindUi.Media,
                access = SourceAccessUi.Partial,
                selected = SourceModeUi.AutoUpload,
                grantedItemCount = 34,
            ),
            onIntent = {},
            navigateNext = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Folder", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceModeFolderPreview() {
    FServerTheme {
        SourceModeScreenContent(
            state = SourceModeState(
                kind = SourceKindUi.Folder,
                selected = SourceModeUi.Sync,
                sourceLabel = "DCIM/Projects",
                sourceFiles = 842,
                sourceBytes = 6_549_123_072L,
            ),
            onIntent = {},
            navigateNext = {},
            navigateUp = {},
        )
    }
}
