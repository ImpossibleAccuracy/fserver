package com.fserver.app.presentation.screens.settings.storage.source

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.settings.storage.source.composable.DeleteUncopiedDialog
import com.fserver.app.presentation.screens.settings.storage.source.composable.SelectionBar
import com.fserver.app.presentation.screens.settings.storage.source.composable.StorageFileRow
import com.fserver.app.presentation.screens.settings.storage.source.composable.StorageSourceHeader
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceIntent
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.LocalFileOpener
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun StorageSourceScreen(
    modifier: Modifier = Modifier,
    key: Destination.Settings.StorageSource,
    viewModel: StorageSourceViewModel = koinViewModel { parametersOf(key) },
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fileOpener = LocalFileOpener.current

    StorageSourceScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        openFile = fileOpener::open,
        navigateUp = navigateUp,
    )
}

@Composable
private fun StorageSourceScreenContent(
    modifier: Modifier = Modifier,
    state: StorageSourceState,
    onIntent: (StorageSourceIntent) -> Unit,
    openFile: (FileBrowserUi.File) -> Unit,
    navigateUp: () -> Unit,
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = state.editing) { onIntent(StorageSourceIntent.EditClosed) }

    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AnimatedContent(
                targetState = state.editing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { editing ->
                if (editing) {
                    SelectionTopBar(state = state, onIntent = onIntent)
                } else {
                    DkTopBar(
                        title = state.label,
                        onBack = navigateUp,
                        actions = {
                            if (!state.isEmpty && state.exists) {
                                DkIconButton(
                                    icon = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.action_edit),
                                    onClick = { onIntent(StorageSourceIntent.EditStarted) },
                                )
                            }
                        },
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.editing,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                SelectionBar(
                    count = state.selected.size,
                    bytes = state.selectedBytes,
                    onDelete = {
                        if (state.selectedWithoutCopy > 0) {
                            confirmingDelete = true
                        } else {
                            onIntent(StorageSourceIntent.DeleteConfirmed)
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> CenteredBox(Modifier.padding(innerPadding)) { DkInlineSpinner() }

            !state.exists -> CenteredBox(Modifier.padding(innerPadding)) {
                DkCaption(
                    text = stringResource(R.string.storage_source_missing),
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            ) {
                item(key = "header") {
                    StorageSourceHeader(
                        modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                        state = state,
                        onSortChange = { onIntent(StorageSourceIntent.SortChanged(it)) },
                        onGroupedChange = { onIntent(StorageSourceIntent.GroupingChanged(it)) },
                    )
                }

                if (state.isEmpty) {
                    item(key = "empty") { EmptyFiles(state = state) }
                } else {
                    files(state = state, onIntent = onIntent, openFile = openFile)
                }
            }
        }
    }

    if (confirmingDelete) {
        DeleteUncopiedDialog(
            count = state.selected.size,
            uncopied = state.selectedWithoutCopy,
            deviceName = state.peer.name,
            onConfirm = { onIntent(StorageSourceIntent.DeleteConfirmed) },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun SelectionTopBar(
    modifier: Modifier = Modifier,
    state: StorageSourceState,
    onIntent: (StorageSourceIntent) -> Unit,
) {
    DkTopBar(
        modifier = modifier,
        title = stringResource(R.string.storage_selected, state.selected.size),
        onBack = { onIntent(StorageSourceIntent.EditClosed) },
        backIcon = Icons.Default.Close,
        backLabel = stringResource(R.string.action_close),
        actions = {
            DkGhostButton(
                text = stringResource(
                    if (state.allSelected) R.string.storage_deselect_all else R.string.storage_select_all
                ),
                onClick = { onIntent(StorageSourceIntent.AllToggled) },
            )
        },
    )
}

private fun LazyListScope.files(
    state: StorageSourceState,
    onIntent: (StorageSourceIntent) -> Unit,
    openFile: (FileBrowserUi.File) -> Unit,
) {
    state.groups.forEach { group ->
        val folder = group.folder
        if (folder != null) {
            item(key = "folder:$folder") {
                DkSectionLabel(
                    modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                    text = folder,
                    trailing = { DkMonoCaption(text = FileSize(group.bytes).formatted()) },
                )
            }
        }

        itemsIndexed(group.files, key = { _, file -> file.id }) { index, file ->
            StorageFileRow(
                file = file,
                deviceName = state.peer.name,
                editing = state.editing,
                selected = file.id in state.selected,
                onOpen = { openFile(file.preview) },
                onToggle = { onIntent(StorageSourceIntent.FileToggled(file.id)) },
                onLongPress = { onIntent(StorageSourceIntent.FileLongPressed(file.id)) },
            )
            if (index != group.files.lastIndex) DkFadingDivider()
        }
    }
}

@Composable
private fun EmptyFiles(
    modifier: Modifier = Modifier,
    state: StorageSourceState,
) {
    DkCaption(
        modifier = modifier.padding(DkSpacing.screenPadding),
        text = if (state.offPhoneFiles > 0) {
            pluralStringResource(
                R.plurals.storage_source_off_phone,
                state.offPhoneFiles,
                state.offPhoneFiles,
                state.peer.name,
            )
        } else {
            stringResource(R.string.storage_source_empty)
        },
    )
}

@Composable
private fun CenteredBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(DkSpacing.screenPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun StorageSourceScreenPreview() {
    FServerTheme {
        StorageSourceScreenContent(
            state = StorageSourceState.Sample,
            onIntent = {},
            openFile = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun StorageSourceScreenEditingPreview() {
    FServerTheme {
        StorageSourceScreenContent(
            state = StorageSourceState.SampleEditing,
            onIntent = {},
            openFile = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun StorageSourceScreenEmptyPreview() {
    FServerTheme {
        StorageSourceScreenContent(
            state = StorageSourceState.SampleOffPhone,
            onIntent = {},
            openFile = {},
            navigateUp = {},
        )
    }
}
