package com.fserver.app.presentation.screens.files.picker

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DKTransparentTopBarColors
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.model.FileKindUi
import com.fserver.app.presentation.model.PickedEntryUi
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
        modifier = Modifier.fillMaxSize(),
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

            Spacer(Modifier.width(12.dp))
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            DkMonoCaption(
                text = pluralStringResource(
                    R.plurals.picker_selected_count,
                    state.entries.size,
                    state.entries.size,
                ),
                modifier = Modifier.padding(
                    horizontal = DkSpacing.screenPadding,
                    vertical = DkSpacing.sm,
                ),
            )

            if (state.isEmpty) {
                PickerEmptyState(modifier = Modifier.fillMaxSize())
            } else {
                PickedEntryList(
                    entries = state.entries,
                    onRemove = { onIntent(FilesPickerIntent.EntryRemoved(it)) },
                )
            }
        }
    }
}

@Composable
private fun PickedEntryList(
    entries: List<PickedEntryUi>,
    onRemove: (PickedEntryUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // The FAB sits over the tail of the list; keep the last row reachable.
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            SwipeToRemoveRow(onRemove = { onRemove(entry) }) {
                PickedEntryRow(entry)
            }
            DkFadingDivider()
        }
    }
}

/**
 * Swipe in either direction to drop the row. The removal is reported once the swipe has
 * settled off-screen; the row then leaves the list by its key, so no reset is needed.
 */
@Composable
private fun SwipeToRemoveRow(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onRemove()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { RemoveBackground(dismissState.dismissDirection) },
        content = { content() },
    )
}

@Composable
private fun RemoveBackground(direction: SwipeToDismissBoxValue) {
    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }

    val backgroundColor by animateColorAsState(
        targetValue = when (direction) {
            SwipeToDismissBoxValue.StartToEnd,
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer

            SwipeToDismissBoxValue.Settled -> Color.Transparent
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = DkSpacing.xl),
        contentAlignment = alignment,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.picker_remove_entry),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PickedEntryRow(entry: PickedEntryUi) {
    DkListRow(
        title = entry.name,
        subtitle = entry.path,
        subtitleStyle = DkType.mono,
        leading = { DkThumbnail(icon = entry.kind.icon()) },
        trailing = entry.detailLabel?.let { detail -> { DkCaption(text = detail) } },
    )
}

@Composable
private fun PickerEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(DkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DkCaption(text = stringResource(R.string.picker_empty_title))
        DkMonoCaption(text = stringResource(R.string.picker_empty_hint))
    }
}

private fun FileKindUi.icon(): ImageVector = when (this) {
    FileKindUi.Folder -> Icons.Default.Folder
    FileKindUi.Image -> Icons.Default.Image
    FileKindUi.Video -> Icons.Default.Movie
    FileKindUi.Audio -> Icons.Default.AudioFile
    FileKindUi.Document -> Icons.Default.Description
    FileKindUi.Other -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesPickerScreenPreview() {
    FServerTheme {
        FilesPickerScreenContent(
            state = FilesPickerState(entries = SampleData.pickedEntries),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
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
