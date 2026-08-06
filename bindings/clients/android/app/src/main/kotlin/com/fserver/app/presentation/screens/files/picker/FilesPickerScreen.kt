package com.fserver.app.presentation.screens.files.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DKTransparentTopBarColors
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.files.picker.composable.PickedEntryList
import com.fserver.app.presentation.screens.files.picker.composable.PickerEmptyState
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerIntent
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun FilesPickerScreen(
    viewModel: FilesPickerViewModel = koinViewModel(),
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FilesPickerScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

/**
 * The selection the user is assembling to send: files and whole directories in one list.
 *
 * A row is dropped by swiping it away rather than by a trailing button — the delete target
 * is the row itself, and nothing here removes anything from the device.
 */
@Composable
private fun FilesPickerScreenContent(
    state: FilesPickerState,
    onIntent: (FilesPickerIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        containerColor = Color.Transparent,
        topBar = {
            DkTopBar(
                title = stringResource(R.string.picker_title),
                colors = DKTransparentTopBarColors,
                onBack = navigateUp,
                actions = {
                    DkGhostButton(
                        text = stringResource(R.string.picker_add),
                        icon = Icons.Default.Add,
                        onClick = { onIntent(FilesPickerIntent.AddClicked) },
                    )
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(
                    horizontal = DkSpacing.screenPadding,
                    vertical = DkSpacing.sm,
                ),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            if (state.isEmpty) {
                PickerEmptyState(modifier = Modifier.fillMaxWidth())
            } else {
                DkMonoCaption(
                    text = pluralStringResource(
                        R.plurals.picker_selected_count,
                        state.entries.size,
                        state.entries.size,
                    ),
                )

                PickedEntryList(
                    entries = state.entries,
                    onRemove = { onIntent(FilesPickerIntent.EntryRemoved(it)) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FilesPickerScreenPreview() {
    FServerTheme {
        FilesPickerScreenContent(
            state = FilesPickerState(entries = FilesPickerState.SampleEntries),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FilesPickerScreenEmptyPreview() {
    FServerTheme {
        FilesPickerScreenContent(
            state = FilesPickerState(),
            onIntent = {},
            navigateUp = {},
        )
    }
}
