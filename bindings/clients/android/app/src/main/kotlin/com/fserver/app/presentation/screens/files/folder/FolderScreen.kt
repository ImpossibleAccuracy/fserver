package com.fserver.app.presentation.screens.files.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.composable.model.labelRes
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMediaTile
import com.fserver.app.presentation.designkit.DkMediaTileBadge
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.files.folder.model.FolderIntent
import com.fserver.app.presentation.screens.files.folder.model.FolderState
import com.fserver.app.presentation.screens.source.shared.preview.composable.icon
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

private val TileGap = 4.dp

@Composable
fun FolderScreen(
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FolderScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

@Composable
private fun FolderScreenContent(
    modifier: Modifier = Modifier,
    state: FolderState,
    onIntent: (FolderIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = state.title,
                onBack = navigateUp,
                actions = {
                    IconButton(onClick = { onIntent(FolderIntent.ViewToggled) }) {
                        Icon(
                            imageVector = if (state.mediaCollection) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Default.GridView
                            },
                            contentDescription = stringResource(R.string.files_view_mode),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            DkCaption(
                modifier = Modifier.padding(
                    horizontal = DkSpacing.screenPadding,
                    vertical = DkSpacing.sm,
                ),
                text = state.summary,
            )

            // TODO: reuse SourcePreview
            if (state.mediaCollection) {
                FolderGrid(
                    modifier = Modifier.weight(1f),
                    items = state.items,
                    onItemClick = { onIntent(FolderIntent.ItemClicked(it)) },
                )
            } else {
                FolderList(
                    modifier = Modifier.weight(1f),
                    items = state.items,
                    onItemClick = { onIntent(FolderIntent.ItemClicked(it)) },
                )
            }

            if (state.showsCloudNotice) {
                DkInfoBox(
                    modifier = Modifier.padding(DkSpacing.screenPadding),
                    text = stringResource(R.string.files_folder_cloud_notice),
                )
            }
        }
    }
}

@Composable
private fun FolderGrid(
    modifier: Modifier = Modifier,
    items: List<FolderState.ItemUi>,
    onItemClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = DkSpacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
        verticalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        items(items, key = { it.id }) { item ->
            DkMediaTile(
                durationLabel = item.file.durationLabel,
                onClick = { onItemClick(item.id) },
                badge = { ItemBadge(item) },
            )
        }
    }
}

@Composable
private fun FolderList(
    modifier: Modifier = Modifier,
    items: List<FolderState.ItemUi>,
    onItemClick: (String) -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            val availabilityIcon = item.file.availability.icon

            DkListRow(
                title = item.file.name,
                subtitle = item.file.durationLabel,
                onClick = { onItemClick(item.id) },
                leading = { DkThumbnail(icon = item.file.kind.icon()) },
                trailing = {
                    if (availabilityIcon != null) {
                        DkIcon(
                            icon = availabilityIcon,
                            contentDescription = stringResource(item.file.availability.labelRes),
                        )
                    }
                },
            )
            DkFadingDivider()
        }
    }
}

@Composable
private fun BoxScope.ItemBadge(item: FolderState.ItemUi) {
    val availabilityIcon = item.file.availability.icon

    when {
        item.pinned -> DkMediaTileBadge(
            icon = Icons.Default.PushPin,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = stringResource(R.string.files_state_pinned),
        )

        availabilityIcon != null -> DkMediaTileBadge(
            icon = availabilityIcon,
            contentDescription = stringResource(item.file.availability.labelRes),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FolderScreenPreview() {
    FServerTheme {
        FolderScreenContent(
            state = FolderState(
                title = "Camera",
                summary = "Offload · older than 30 days · 1 240",
                items = FolderState.SampleItems,
                showsCloudNotice = true,
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "List", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FolderScreenListPreview() {
    FServerTheme {
        FolderScreenContent(
            state = FolderState(
                title = "Documents",
                summary = "Sync · 312 items",
                mediaCollection = false,
                items = FolderState.SampleItems,
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}
