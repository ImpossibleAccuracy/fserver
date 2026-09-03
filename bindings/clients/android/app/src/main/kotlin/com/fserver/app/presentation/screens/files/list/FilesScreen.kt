package com.fserver.app.presentation.screens.files.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.composable.DkFab
import com.fserver.app.presentation.composable.model.FileAvailabilityUi
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.FileUi
import com.fserver.app.presentation.composable.model.FilesViewModeUi
import com.fserver.app.presentation.composable.model.TreeNodeUi
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMediaTile
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSegmentedControl
import com.fserver.app.presentation.designkit.DkSegmentedOption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkTreeRow
import com.fserver.app.presentation.screens.files.list.model.FilesIntent
import com.fserver.app.presentation.screens.request.shared.composable.SyncRequestBanner
import com.fserver.app.presentation.screens.files.list.model.FilesState
import com.fserver.app.presentation.screens.source.shared.preview.composable.icon
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun FilesScreen(
    viewModel: FilesViewModel = koinViewModel(),
    navigateToActions: () -> Unit,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateToSyncRequest: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FilesScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToActions = navigateToActions,
        navigateToConnect = navigateToConnect,
        navigateToSourcePick = navigateToSourcePick,
        navigateToSyncRequest = navigateToSyncRequest,
    )
}

/**
 * The server's tree, in whichever of the three shapes the user picked.
 *
 * `↓` means the bytes are still on the server and a tap fetches them; `✓` means the file
 * is already here. That distinction is also where offload placeholders will land later.
 */
@Composable
private fun FilesScreenContent(
    state: FilesState,
    onIntent: (FilesIntent) -> Unit,
    navigateToActions: () -> Unit,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateToSyncRequest: (String) -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = state.serverName.ifEmpty { stringResource(R.string.files_title) },
                actions = {
                    IconButton(onClick = { onIntent(FilesIntent.SearchClicked) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // With nothing to show, the two ways out are already in the middle of the screen —
            // a button offering the same fork on top of them would be the third copy of it.
            if (!state.isEmpty) {
                DkFab(
                    icon = Icons.Default.Add,
                    label = stringResource(R.string.files_send_file),
                    onClick = navigateToActions,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            state.syncRequest?.let { request ->
                SyncRequestBanner(
                    modifier = Modifier.padding(
                        horizontal = DkSpacing.screenPadding,
                        vertical = DkSpacing.sm,
                    ),
                    request = request,
                    waiting = state.syncRequestsWaiting,
                    onClick = { navigateToSyncRequest(request.sourceId) },
                )
            }

            if (state.isEmpty) {
                FilesEmptyState(
                    navigateToConnect = navigateToConnect,
                    navigateToSourcePick = navigateToSourcePick,
                    modifier = Modifier.weight(1f),
                )
                return@Column
            }

            Column(
                modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkMonoCaption(
                    text = if (state.viewMode == FilesViewModeUi.Grid) {
                        stringResource(
                            R.string.files_breadcrumb_items,
                            state.breadcrumb,
                            state.itemCount,
                        )
                    } else {
                        state.breadcrumb
                    },
                )
                DkSegmentedControl(
                    options = listOf(
                        DkSegmentedOption(
                            value = FilesViewModeUi.List,
                            label = stringResource(R.string.files_view_list),
                            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        ),
                        DkSegmentedOption(
                            value = FilesViewModeUi.Grid,
                            label = stringResource(R.string.files_view_grid),
                            icon = Icons.Default.GridView,
                        ),
                        DkSegmentedOption(
                            value = FilesViewModeUi.Tree,
                            label = stringResource(R.string.files_view_tree),
                            icon = Icons.Default.AccountTree,
                        ),
                    ),
                    selected = state.viewMode,
                    onSelect = { onIntent(FilesIntent.ViewModeSelected(it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (state.viewMode) {
                FilesViewModeUi.List -> FilesListView(
                    files = state.files,
                    onFileClick = { onIntent(FilesIntent.FileClicked(it)) },
                )

                FilesViewModeUi.Grid -> FilesGridView(
                    tiles = state.gridTiles,
                    onFileClick = { onIntent(FilesIntent.FileClicked(it)) },
                )

                FilesViewModeUi.Tree -> FilesTreeView(nodes = state.tree)
            }
        }
    }
}

/**
 * Where a skipped onboarding lands, and where the app sits until something is connected.
 *
 * It carries the same fork the "+" button opens, so the tab is never a dead end — the bottom
 * bar stays usable underneath it.
 */
@Composable
private fun FilesEmptyState(
    navigateToConnect: () -> Unit,
    navigateToSourcePick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DkPlaceholderBox(
            label = stringResource(R.string.files_empty_illustration),
            modifier = Modifier.height(120.dp),
        )
        Text(
            modifier = Modifier.padding(top = DkSpacing.xl),
            text = stringResource(R.string.files_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            modifier = Modifier.padding(top = DkSpacing.sm),
            text = stringResource(R.string.files_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        DkPrimaryButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DkSpacing.xl),
            text = stringResource(R.string.action_connect),
            onClick = navigateToConnect,
        )
        DkGhostButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DkSpacing.sm),
            text = stringResource(R.string.fork_send_title),
            onClick = navigateToSourcePick,
        )
    }
}

@Composable
private fun FilesListView(
    files: List<FileUi>,
    onFileClick: (FileUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(files, key = { it.id }) { file ->
            DkListRow(
                title = file.name,
                subtitle = file.subtitleLabel(),
                onClick = { onFileClick(file) },
                leading = {
                    DkThumbnail(
                        icon = if (file.kind == FileKindUi.Folder) {
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        } else {
                            file.kind.icon()
                        }
                    )
                },
                trailing = { AvailabilityMarker(file) },
            )
            DkFadingDivider()
        }
    }
}

@Composable
private fun FilesGridView(
    tiles: List<FileUi>,
    onFileClick: (FileUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = DkSpacing.screenPadding,
            vertical = DkSpacing.sm,
        ),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(tiles, key = { it.id }) { tile ->
            DkMediaTile(
                extensionLabel = tile.extensionLabel,
                durationLabel = tile.durationLabel,
                remote = tile.availability == FileAvailabilityUi.OnServer,
                onClick = { onFileClick(tile) },
            )
        }
    }
}

@Composable
private fun FilesTreeView(nodes: List<TreeNodeUi>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = DkSpacing.md, vertical = DkSpacing.xs),
    ) {
        items(nodes, key = { it.id }) { node ->
            DkTreeRow(
                title = node.name,
                expandable = node.isFolder,
                trailingText = node.childCountLabel,
                onClick = {},
                trailing = {
                    when (node.availability) {
                        FileAvailabilityUi.OnServer -> Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.files_state_remote),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )

                        FileAvailabilityUi.OnDevice -> Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.files_state_local),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )

                        null -> Unit
                    }
                },
            )
        }
    }
}

@Composable
private fun AvailabilityMarker(file: FileUi) {
    if (file.kind == FileKindUi.Folder) return
    when (file.availability) {
        FileAvailabilityUi.OnServer -> Icon(
            imageVector = Icons.Default.Download,
            contentDescription = stringResource(R.string.files_state_remote),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )

        FileAvailabilityUi.OnDevice -> Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.files_state_local),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun FileUi.subtitleLabel(): String? = when {
    kind == FileKindUi.Folder && childCount != null ->
        pluralStringResource(R.plurals.files_folder_count, childCount, childCount)

    sizeLabel != null && dateLabel != null -> "$sizeLabel · $dateLabel"
    else -> sizeLabel ?: dateLabel
}

@Preview(showBackground = true)
@Composable
private fun FilesScreenPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(
                serverName = SampleData.CURRENT_SERVER,
                breadcrumb = SampleData.BREADCRUMB,
                files = SampleData.files,
                gridTiles = SampleData.gridTiles,
                tree = SampleData.tree,
                itemCount = SampleData.GRID_ITEM_COUNT,
            ),
            onIntent = {},
            navigateToActions = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequest = {},
        )
    }
}

@Preview(name = "Nothing connected", showBackground = true)
@Composable
private fun FilesScreenEmptyPreview() {
    FServerTheme {
        FilesScreenContent(
            state = FilesState(),
            onIntent = {},
            navigateToActions = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequest = {},
        )
    }
}
