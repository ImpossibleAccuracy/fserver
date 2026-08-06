package com.fserver.app.presentation.screens.files.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.composable.DkFab
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMediaTile
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSegmentedControl
import com.fserver.app.presentation.designkit.DkSegmentedOption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkTreeRow
import com.fserver.app.presentation.model.FileAvailabilityUi
import com.fserver.app.presentation.model.FileKindUi
import com.fserver.app.presentation.model.FileUi
import com.fserver.app.presentation.model.FilesViewModeUi
import com.fserver.app.presentation.model.TreeNodeUi
import com.fserver.app.presentation.screens.files.list.composable.IncomingFilesSheet
import com.fserver.app.presentation.screens.files.list.model.FilesIntent
import com.fserver.app.presentation.screens.files.list.model.FilesState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun FilesScreen(
    viewModel: FilesViewModel = koinViewModel(),
    navigateToTransfers: () -> Unit,
    navigateToPicker: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FilesScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToTransfers = navigateToTransfers,
        navigateToPicker = navigateToPicker,
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
    navigateToTransfers: () -> Unit,
    navigateToPicker: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = state.serverName,
                actions = {
                    IconButton(onClick = { onIntent(FilesIntent.SearchClicked) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                    // Stand-in trigger: until :core pushes real incoming requests, this is
                    // how the receive sheet can be reached.
                    IconButton(onClick = { onIntent(FilesIntent.IncomingDemoRequested) }) {
                        Icon(
                            imageVector = Icons.Default.MoveToInbox,
                            contentDescription = stringResource(R.string.incoming_demo_trigger),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            DkFab(
                icon = Icons.Default.Add,
                label = stringResource(R.string.files_send_file),
                onClick = navigateToPicker,
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
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

        // The receive sheet is offered over the file list: accepting moves the user to the
        // queue, where the transfer it just started is visible.
        state.incomingRequest?.let { request ->
            IncomingFilesSheet(
                request = request,
                onAccept = {
                    onIntent(FilesIntent.IncomingRequestDismissed)
                    navigateToTransfers()
                },
                onDecline = { onIntent(FilesIntent.IncomingRequestDismissed) },
                onDismiss = { onIntent(FilesIntent.IncomingRequestDismissed) },
            )
        }
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
                depth = node.depth,
                expandable = node.isFolder,
                expanded = node.expanded,
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
            navigateToTransfers = {},
            navigateToPicker = {},
        )
    }
}
