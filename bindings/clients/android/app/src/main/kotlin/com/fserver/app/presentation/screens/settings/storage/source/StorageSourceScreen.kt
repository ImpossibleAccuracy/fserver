package com.fserver.app.presentation.screens.settings.storage.source

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.fserver.app.presentation.composable.ObserveEffects
import com.fserver.app.presentation.composable.SelectionTopBar
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.settings.storage.source.composable.DeleteUncopiedDialog
import com.fserver.app.presentation.screens.settings.storage.source.composable.SelectionBar
import com.fserver.app.presentation.screens.settings.storage.source.composable.StorageSourceHeader
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceIntent
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceUiEffect
import com.fserver.app.presentation.shared.browser.FileBrowser
import com.fserver.app.presentation.shared.browser.FileBrowserNavigation
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.LocalFileOpener
import com.fserver.app.presentation.theme.FServerTheme
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

    ObserveEffects(viewModel.uiEffects) { effect ->
        when (effect) {
            is StorageSourceUiEffect.OpenFile -> fileOpener.open(effect.file)
        }
    }

    state?.let { state ->
        StorageSourceScreenContent(
            modifier = modifier,
            state = state,
            onIntent = viewModel::onIntent,
            navigateUp = navigateUp,
        )
    }
}

@Composable
private fun StorageSourceScreenContent(
    modifier: Modifier = Modifier,
    state: StorageSourceState,
    onIntent: (StorageSourceIntent) -> Unit,
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
                    SelectionTopBar(
                        title = stringResource(R.string.storage_selected, state.selected.size),
                        onClose = { onIntent(StorageSourceIntent.EditClosed) },
                        actions = {
                            DkGhostButton(
                                text = stringResource(
                                    if (state.allSelected) R.string.storage_deselect_all
                                    else R.string.storage_select_all
                                ),
                                onClick = { onIntent(StorageSourceIntent.AllToggled) },
                            )
                        },
                    )
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

            state.isEmpty -> Column(modifier = Modifier.padding(innerPadding)) {
                SourceHeader(state = state, onIntent = onIntent)
                EmptyFiles(state = state)
            }

            else -> FileBrowser(
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                preview = state.preview,
                navigation = rememberFolderNavigation(state.tree),
                selection = if (state.editing) {
                    FileBrowserSelection(
                        selected = state.selected,
                        onToggle = { onIntent(StorageSourceIntent.FileToggled(it.id)) },
                    )
                } else {
                    null
                },
                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                header = { SourceHeader(state = state, onIntent = onIntent) },
                onFileClick = { onIntent(StorageSourceIntent.FileClicked(it.id)) },
                onFileLongClick = { onIntent(StorageSourceIntent.FileLongPressed(it.id)) },
            )
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
private fun SourceHeader(
    modifier: Modifier = Modifier,
    state: StorageSourceState,
    onIntent: (StorageSourceIntent) -> Unit,
) {
    StorageSourceHeader(
        modifier = modifier.padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.sm),
        state = state,
        onSortChange = { onIntent(StorageSourceIntent.SortChanged(it)) },
        onGroupedChange = { onIntent(StorageSourceIntent.GroupingChanged(it)) },
    )
}

/**
 * The open folder, kept by path: the tree is rebuilt on every transfer update, and a folder held
 * by value would close each time.
 */
@Composable
private fun rememberFolderNavigation(tree: FileBrowserUi.Tree): FileBrowserNavigation {
    var openedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val opened = openedPath?.let { tree.trailTo(it).lastOrNull() }

    return FileBrowserNavigation(
        opened = opened,
        onOpen = { openedPath = it.path },
        onUp = { openedPath = tree.parentOf(opened)?.path },
    )
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
            navigateUp = {},
        )
    }
}
