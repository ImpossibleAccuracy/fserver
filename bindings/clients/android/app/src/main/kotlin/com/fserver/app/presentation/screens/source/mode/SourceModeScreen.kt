package com.fserver.app.presentation.screens.source.mode

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
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
import com.fserver.app.presentation.screens.source.shared.composable.SourceChoiceRow
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.modeTitleRes
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

@Composable
fun SourceModeScreen(
    handler: SourceModeHandler,
    navigateNext: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state = handler.state.collectAsStateWithLifecycle().value ?: return

    SourceModeScreenContent(
        state = state,
        onIntent = handler::onIntent,
        navigateNext = {
            handler.onIntent(SourceModeIntent.Confirmed)
            navigateNext()
        },
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
            when (val type = state.accessType) {
                is SourceModeState.AccessType.Full -> {
                    Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs)) {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (type.files > 0) {
                            DkMonoCaption(
                                text = stringResource(
                                    R.string.source_scan_folder_summary,
                                    type.files,
                                    type.size.formatted(),
                                )
                            )
                        }
                    }
                }

                is SourceModeState.AccessType.Partial -> {
                    PartialAccessNotice(
                        count = type.grantedItemCount,
                    )
                    DkCaption(
                        text = pluralStringResource(
                            R.plurals.source_mode_partial_hint,
                            type.grantedItemCount,
                            type.grantedItemCount,
                        )
                    )
                }

                null -> {
                    DkCaption(text = stringResource(R.string.source_mode_hint))
                }
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
    modifier: Modifier = Modifier,
    count: Int,
) {
    DkCard(modifier = modifier, outlined = true) {
        Text(
            modifier = Modifier.weight(1f),
            text = pluralStringResource(R.plurals.source_mode_partial_title, count, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
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

@Preview(showBackground = true)
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

@Preview(name = "Partial access", showBackground = true)
@Composable
private fun SourceModePartialPreview() {
    FServerTheme {
        SourceModeScreenContent(
            state = SourceModeState(
                kind = SourceKindUi.Media,
                selected = SourceModeUi.AutoUpload,
                accessType = SourceModeState.AccessType.Partial(
                    grantedItemCount = 34,
                ),
            ),
            onIntent = {},
            navigateNext = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Folder", showBackground = true)
@Composable
private fun SourceModeFolderPreview() {
    FServerTheme {
        SourceModeScreenContent(
            state = SourceModeState(
                kind = SourceKindUi.Folder,
                selected = SourceModeUi.Sync,
                accessType = SourceModeState.AccessType.Full(
                    label = "DCIM/Projects",
                    files = 842,
                    size = FileSize(6_549_123_072L),
                ),
            ),
            onIntent = {},
            navigateNext = {},
            navigateUp = {},
        )
    }
}
