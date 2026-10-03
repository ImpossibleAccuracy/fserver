package com.fserver.app.presentation.screens.files

import com.fserver.app.presentation.shared.browser.model.FileKey
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.fserver.app.presentation.composable.ObserveEffects
import com.fserver.app.presentation.composable.SelectionTopBar
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkProgressBar
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.composable.TextEditorDialog
import com.fserver.app.presentation.screens.files.composable.Breadcrumbs
import com.fserver.app.presentation.screens.files.composable.DeleteFilesDialog
import com.fserver.app.presentation.screens.files.composable.FileActionsMenu
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.composable.FilesFilterSheet
import com.fserver.app.presentation.screens.files.model.FilesIntent
import com.fserver.app.presentation.screens.files.model.FilesState
import com.fserver.app.presentation.screens.files.model.FilesUiEffect
import com.fserver.app.presentation.shared.browser.FileBrowser
import com.fserver.app.presentation.shared.browser.FileBrowserNavigation
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.composable.FileSortAction
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.SampleFiles
import com.fserver.app.presentation.shared.viewer.LocalFileOpener
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FilesScreen(
    modifier: Modifier = Modifier,
    key: Destination.Files,
    viewModel: FilesViewModel = koinViewModel { parametersOf(key) },
    navigateToSourcePick: () -> Unit,
    navigateToImageEditor: (FileKey) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fileOpener = LocalFileOpener.current

    ObserveEffects(viewModel.uiEffects) { effect ->
        when (effect) {
            is FilesUiEffect.OpenFile -> fileOpener.open(effect.file)
        }
    }

    FilesScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToSourcePick = navigateToSourcePick,
        navigateToImageEditor = navigateToImageEditor,
        navigateUp = navigateUp,
    )
}

@Composable
private fun FilesScreenContent(
    modifier: Modifier = Modifier,
    state: FilesState,
    onIntent: (FilesIntent) -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateToImageEditor: (FileKey) -> Unit,
    navigateUp: () -> Unit,
) {
    val opened = state.openedDirectory
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable(stateSaver = FileKeySaver) { mutableStateOf<FileKey?>(null) }
    var deleting by remember { mutableStateOf<Set<FileKey>?>(null) }

    val onFileAction = { action: FilesState.FileActionUi, files: Set<FileKey> ->
        when (action) {
            FilesState.FileActionUi.Edit -> {
                onIntent(FilesIntent.EditClosed)
                navigateToImageEditor(files.single())
            }
            FilesState.FileActionUi.Rename -> renaming = files.single()
            FilesState.FileActionUi.Pin -> onIntent(FilesIntent.PinRequested(files, pinned = true))
            FilesState.FileActionUi.Unpin -> onIntent(FilesIntent.PinRequested(files, pinned = false))
            FilesState.FileActionUi.Delete -> deleting = files
        }
    }

    val onSelectionAction = { action: FilesState.FileActionUi ->
        when {
            action == FilesState.FileActionUi.Pin || action == FilesState.FileActionUi.Unpin -> onIntent(
                FilesIntent.PinRequested(state.selectionPinTargets, pinned = action == FilesState.FileActionUi.Pin)
            )

            state.selectedFolders.isEmpty() -> onFileAction(action, state.selected)

            // TODO: rename and delete of folders - FilesController has no folder operations yet.
            else -> Unit
        }
    }

    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AnimatedContent(
                targetState = state.editing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { editing ->
                if (editing) {
                    SelectionTopBar(
                        title = stringResource(R.string.files_selected, state.selectedCount),
                        onClose = { onIntent(FilesIntent.EditClosed) },
                        actions = {
                            FileActionsMenu(
                                actions = state.selectionActions,
                                onAction = onSelectionAction,
                            )
                        },
                    )
                } else {
                    DkTopBar(
                        title = opened?.name ?: stringResource(R.string.files_title),
                        subtitle = state.filterSummary(),
                        onBack = {
                            if (opened != null) onIntent(FilesIntent.FolderUp) else navigateUp()
                        },
                        actions = {
                            FileSortAction(
                                sort = state.sort,
                                ascending = state.sortAscending,
                                onSelect = { onIntent(FilesIntent.SortSelected(it)) },
                            )

                            // The filters are fixed while a folder is open; the subtitle says which.
                            AnimatedVisibility(
                                visible = opened == null,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                            ) {
                                FilterAction(
                                    isFiltered = state.isFiltered,
                                    onClick = { showFilters = true },
                                )
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        FilesContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
            state = state,
            onIntent = onIntent,
            onFileAction = onFileAction,
            navigateToSourcePick = navigateToSourcePick,
        )
    }

    BackHandler(enabled = state.editing) { onIntent(FilesIntent.EditClosed) }

    renaming?.let { file ->
        TextEditorDialog(
            title = stringResource(R.string.files_rename_title),
            label = stringResource(R.string.files_rename_label),
            initialValue = state.file(file)?.name.orEmpty(),
            onDismiss = { renaming = null },
            onConfirm = {
                onIntent(FilesIntent.RenameConfirmed(file, it))
                renaming = null
            },
        )
    }

    deleting?.let { files ->
        DeleteFilesDialog(
            count = files.size,
            onConfirm = { onIntent(FilesIntent.DeleteConfirmed(files)) },
            onDismiss = { deleting = null },
        )
    }

    if (showFilters) {
        FilesFilterSheet(
            sources = state.sources,
            selectedSourceId = state.selectedSourceId,
            filter = state.filter,
            onApply = { sourceId, filter -> onIntent(FilesIntent.FiltersApplied(sourceId, filter)) },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun FilterAction(
    isFiltered: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (isFiltered) Badge() }) {
            Icon(
                imageVector = Icons.Default.FilterAlt,
                contentDescription = stringResource(R.string.files_filter_title),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesContent(
    modifier: Modifier = Modifier,
    /** Scrolled under, not cut off: the lists draw behind the navigation bar. */
    contentPadding: PaddingValues,
    state: FilesState,
    onIntent: (FilesIntent) -> Unit,
    onFileAction: (FilesState.FileActionUi, Set<FileKey>) -> Unit,
    navigateToSourcePick: () -> Unit,
) {
    val opened = state.openedDirectory

    Column(modifier = modifier.fillMaxSize()) {
        val trail = rememberLastNotNull(state.openedTrail.takeIf { it.isNotEmpty() }).orEmpty()
        AnimatedVisibility(visible = opened != null) {
            Breadcrumbs(
                modifier = Modifier.fillMaxWidth(),
                rootLabel = stringResource(R.string.files_title),
                crumbs = trail.map { it.name },
                onRootClick = { onIntent(FilesIntent.FolderClosed) },
                onCrumbClick = { onIntent(FilesIntent.FolderOpened(trail[it].path)) },
            )
        }

        when {
            state.entries == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                DkInlineSpinner()
            }

            state.entries.preview.isEmpty -> FilesEmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(contentPadding),
                reason = state.emptyReason,
                sourceLabel = state.selectedSource?.label,
                navigateToSourcePick = navigateToSourcePick,
            )

            // The pull only triggers the sync: its spinner lets go at once, and the bar on top
            // reports the run however it was started.
            else -> PullToRefreshBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                isRefreshing = false,
                onRefresh = { onIntent(FilesIntent.RefreshRequested) },
                enabled = opened == null,
            ) {
                val entries = state.entries
                // Keyed by what the tree was built with, so a re-sorted or re-filtered list opens
                // at the top instead of chasing its old first item to wherever it moved.
                key(entries.filter, entries.sourceId, entries.sort, entries.sortAscending) {
                    FileBrowser(
                        modifier = Modifier.fillMaxSize(),
                        preview = entries.preview,
                        // The notice below takes the inset instead when it is showing.
                        contentPadding = if (state.showsCloudNotice) PaddingValues() else contentPadding,
                        navigation = FileBrowserNavigation(
                            opened = opened,
                            onOpen = { onIntent(FilesIntent.FolderOpened(it.path)) },
                            onUp = { onIntent(FilesIntent.FolderUp) },
                        ),
                        selection = if (state.editing) {
                            FileBrowserSelection(
                                selected = state.selected,
                                onToggle = { onIntent(FilesIntent.EntryToggled(it.indexedKey)) },
                                selectedDirectories = state.selectedFolders,
                                onToggleDirectory = { onIntent(FilesIntent.FolderToggled(it.path)) },
                            )
                        } else {
                            null
                        },
                        onFileClick = { onIntent(FilesIntent.EntryClicked(it.indexedKey)) },
                        onFileLongClick = { onIntent(FilesIntent.EntryLongPressed(it.indexedKey)) },
                        onDirectoryLongClick = { onIntent(FilesIntent.FolderLongPressed(it.path)) },
                        fileMenu = { file ->
                            val key = file.indexedKey
                            val actions = state.actionsFor(key)
                            if (actions.isNotEmpty()) {
                                FileActionsMenu(
                                    actions = actions,
                                    onAction = { onFileAction(it, setOf(key)) },
                                )
                            }
                        },
                    )
                }

                // Qualified: the enclosing Column's scoped overload would win otherwise.
                androidx.compose.animation.AnimatedVisibility(
                    modifier = Modifier.align(Alignment.TopCenter),
                    visible = state.isSyncing,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    DkProgressBar(progress = null)
                }
            }
        }

        AnimatedVisibility(visible = state.showsCloudNotice) {
            DkInfoBox(
                modifier = Modifier
                    .padding(DkSpacing.screenPadding)
                    .padding(contentPadding),
                text = stringResource(R.string.files_folder_cloud_notice),
            )
        }
    }
}

@Composable
private fun FilesEmptyState(
    modifier: Modifier = Modifier,
    reason: FilesState.EmptyReasonUi,
    sourceLabel: String?,
    navigateToSourcePick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DkPlaceholderBox(
            modifier = Modifier.height(120.dp),
            label = stringResource(R.string.files_empty_illustration),
        )
        Text(
            modifier = Modifier.padding(top = DkSpacing.xl),
            text = when (reason) {
                FilesState.EmptyReasonUi.NoSourceFiles -> stringResource(
                    R.string.files_empty_source_title,
                    sourceLabel.orEmpty(),
                )

                else -> stringResource(reason.titleRes)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            modifier = Modifier.padding(top = DkSpacing.sm),
            text = stringResource(reason.bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // A filter is the reason for the rest, and the filter button is what undoes it.
        if (reason == FilesState.EmptyReasonUi.NoFiles) {
            DkPrimaryButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DkSpacing.xl),
                text = stringResource(R.string.fork_send_title),
                onClick = navigateToSourcePick,
            )
        }
    }
}

/** The source and filter the feed is seen through, or null when neither narrows it. */
@Composable
private fun FilesState.filterSummary(): String? = listOfNotNull(
    selectedSource?.label,
    when (filter) {
        FilesState.FilterUi.All -> null
        FilesState.FilterUi.Local -> stringResource(R.string.files_filter_local)
        FilesState.FilterUi.Cloud -> stringResource(R.string.files_filter_cloud)
        FilesState.FilterUi.Pinned -> stringResource(R.string.files_filter_pinned)
    },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

private val FileKeySaver = Saver<FileKey?, ArrayList<String>>(
    save = { key -> key?.let { arrayListOf(it.fileId, it.sourceId) } },
    restore = { FileKey(fileId = it[0], sourceId = it[1]) },
)

/** [value], or the last non-null one it had: what an exit animation keeps drawing. */
@Composable
private fun <T : Any> rememberLastNotNull(value: T?): T? {
    val last = remember { LastValue<T>() }
    if (value != null) last.value = value
    return last.value
}

private class LastValue<T : Any> {
    var value: T? = null
}

@get:StringRes
private val FilesState.EmptyReasonUi.titleRes: Int
    get() = when (this) {
        FilesState.EmptyReasonUi.NoFiles -> R.string.files_empty_no_files_title
        FilesState.EmptyReasonUi.NoLocalFiles -> R.string.files_empty_local_title
        FilesState.EmptyReasonUi.NoCloudFiles -> R.string.files_empty_cloud_title
        FilesState.EmptyReasonUi.NoPinnedFiles -> R.string.files_empty_pinned_title
        FilesState.EmptyReasonUi.NoSourceFiles -> R.string.files_empty_source_title
    }

@get:StringRes
private val FilesState.EmptyReasonUi.bodyRes: Int
    get() = when (this) {
        FilesState.EmptyReasonUi.NoFiles -> R.string.files_empty_no_files_body
        FilesState.EmptyReasonUi.NoLocalFiles -> R.string.files_empty_local_body
        FilesState.EmptyReasonUi.NoCloudFiles -> R.string.files_empty_cloud_body
        FilesState.EmptyReasonUi.NoPinnedFiles -> R.string.files_empty_pinned_body
        FilesState.EmptyReasonUi.NoSourceFiles -> R.string.files_empty_source_body
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesScreenPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                sources = FilesState.SampleSources,
                entries = FilesState.SampleEntries,
            ),
            onIntent = {},
            navigateToSourcePick = {},
            navigateToImageEditor = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Folder open, filtered", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesScreenFolderPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                sources = FilesState.SampleSources,
                selectedSourceId = "camera",
                filter = FilesState.FilterUi.Local,
                entries = FilesState.SampleEntries,
                openedPath = "/DCIM",
            ),
            onIntent = {},
            navigateToSourcePick = {},
            navigateToImageEditor = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Nothing matches", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesScreenEmptyPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                filter = FilesState.FilterUi.Cloud,
                entries = FilesState.FeedUi(
                    preview = FileBrowserUi.Tree(),
                    filter = FilesState.FilterUi.Cloud,
                    sourceId = null,
                ),
            ),
            onIntent = {},
            navigateToSourcePick = {},
            navigateToImageEditor = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Selecting", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FilesScreenSelectingPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                sources = FilesState.SampleSources,
                entries = FilesState.SampleEntries,
                openedPath = "/DCIM",
                editing = true,
                selected = setOf(FileBrowserUi.SampleFiles[1].indexedKey),
            ),
            onIntent = {},
            navigateToSourcePick = {},
            navigateToImageEditor = {},
            navigateUp = {},
        )
    }
}
