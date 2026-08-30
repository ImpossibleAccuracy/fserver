package com.fserver.app.presentation.screens.source.mode

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.screens.source.shared.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.modeTitleRes
import com.fserver.app.presentation.screens.source.shared.titleRes
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.shared.composable.SourceChoiceRow
import com.fserver.app.presentation.screens.source.mode.model.SourceModeIntent
import com.fserver.app.presentation.screens.source.mode.model.SourceModeState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SourceModeScreen(
    key: Destination.Source.Mode,
    viewModel: SourceModeViewModel = koinViewModel { parametersOf(key) },
    navigateToTarget: (SourceModeUi) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SourceModeScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToTarget = navigateToTarget,
        navigateUp = navigateUp,
    )
}

/**
 * The same screen for every branch — only the header above the list changes.
 *
 * Photos offer two modes, a folder and the whole device offer four; a partial grant keeps
 * saying so at the top, because it is a lasting state rather than something that happened once.
 */
@Composable
private fun SourceModeScreenContent(
    state: SourceModeState,
    onIntent: (SourceModeIntent) -> Unit,
    navigateToTarget: (SourceModeUi) -> Unit,
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
                    onClick = { state.selected?.let(navigateToTarget) },
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
                    DkMonoCaption(text = state.sourceDetail)
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
                    recommended = mode == state.modes.first() && state.kind == SourceKindUi.Photos,
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
    SourceModeUi.AutoUpload -> if (kind == SourceKindUi.Photos) {
        R.string.mode_autoupload_photos_subtitle
    } else {
        R.string.mode_autoupload_subtitle
    }

    SourceModeUi.Offload -> if (kind == SourceKindUi.Photos) {
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
                kind = SourceKindUi.Photos,
                selected = SourceModeUi.AutoUpload,
            ),
            onIntent = {},
            navigateToTarget = {},
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
                kind = SourceKindUi.Photos,
                access = SourceAccessUi.Partial,
                selected = SourceModeUi.AutoUpload,
                grantedItemCount = 34,
            ),
            onIntent = {},
            navigateToTarget = {},
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
                sourceDetail = "842 files · 6.1 GB",
            ),
            onIntent = {},
            navigateToTarget = {},
            navigateUp = {},
        )
    }
}
