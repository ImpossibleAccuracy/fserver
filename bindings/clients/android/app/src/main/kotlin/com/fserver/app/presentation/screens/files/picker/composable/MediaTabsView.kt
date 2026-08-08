package com.fserver.app.presentation.screens.files.picker.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMediaTile
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkSegmentedControl
import com.fserver.app.presentation.designkit.DkSegmentedOption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.model.FileKindUi
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState.MediaGrouping
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState.MediaItemUi

private const val MEDIA_GRID_COLUMNS = 3
private val MediaListMaxHeight = 420.dp

/**
 * MediaStore browsing: one tab per group, grouped either by media type or by the folder the
 * rows came from. Selection is a set of media ids held above the tabs, so switching tabs — or
 * regrouping entirely — keeps everything already ticked.
 *
 * Images and videos are grid tiles because they are recognised by their frame, not their name;
 * audio and everything else stay rows, where the name is what identifies them.
 */
@Composable
fun MediaTabsView(
    grouping: MediaGrouping,
    tabs: List<FilesPickerState.MediaTabUi>,
    activeTab: FilesPickerState.MediaTabUi?,
    onGroupingSelected: (MediaGrouping) -> Unit,
    onTabSelected: (String) -> Unit,
    onItemToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DkSegmentedControl(
            options = listOf(
                DkSegmentedOption(
                    value = MediaGrouping.Type,
                    label = stringResource(R.string.picker_media_group_type),
                ),
                DkSegmentedOption(
                    value = MediaGrouping.Directory,
                    label = stringResource(R.string.picker_media_group_folder),
                ),
            ),
            selected = grouping,
            onSelect = onGroupingSelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DkSpacing.screenPadding),
        )

        if (activeTab == null) {
            DkMonoCaption(
                modifier = Modifier.padding(DkSpacing.xl),
                text = stringResource(R.string.picker_media_empty),
            )
            return@Column
        }

        PrimaryScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.id == activeTab.id }.coerceAtLeast(0),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = DkSpacing.screenPadding,
            divider = { DkFadingDivider() },
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab.id == activeTab.id,
                    onClick = { onTabSelected(tab.id) },
                    text = {
                        Text(
                            text = tab.label(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val gridRows = activeTab.visualItems.chunked(MEDIA_GRID_COLUMNS)
        val listItems = activeTab.otherItems

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MediaListMaxHeight),
            contentPadding = PaddingValues(vertical = DkSpacing.sm),
        ) {
            items(gridRows, key = { row -> row.first().id }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = DkSpacing.screenPadding,
                            vertical = 1.5.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    row.forEach { item ->
                        MediaTile(
                            item = item,
                            onClick = { onItemToggle(item.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps the last row's tiles the same size as every other row's.
                    repeat(MEDIA_GRID_COLUMNS - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            items(listItems, key = { it.id }) { item ->
                DkListRow(
                    title = item.name,
                    onClick = { onItemToggle(item.id) },
                    leading = { DkThumbnail(icon = item.kind.icon()) },
                    trailing = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item.detailLabel?.let { DkCaption(text = it) }
                            Checkbox(
                                checked = item.selected,
                                onCheckedChange = { onItemToggle(item.id) },
                                modifier = Modifier.size(24.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.outline,
                                ),
                            )
                        }
                    },
                )
                DkFadingDivider()
            }
        }
    }
}

/**
 * A grid cell. There is no thumbnail loader in the app yet, so the tile carries its kind icon
 * and the selection marker sits on top of it — the same place a real preview would go.
 */
@Composable
private fun MediaTile(
    item: MediaItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        DkMediaTile(
            modifier = Modifier.fillMaxWidth(),
            extensionLabel = item.extensionLabel,
            onClick = onClick,
        )

        if (item.extensionLabel == null) {
            Icon(
                imageVector = item.kind.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp),
            )
        }

        if (item.selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp),
                    ),
            )
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.picker_entry_selected),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(2.dp)
                    .size(12.dp),
            )
        }
    }
}

@Composable
private fun FilesPickerState.MediaTabUi.label(): String = when (kind) {
    FileKindUi.Image -> stringResource(R.string.picker_media_kind_image)
    FileKindUi.Video -> stringResource(R.string.picker_media_kind_video)
    FileKindUi.Audio -> stringResource(R.string.picker_media_kind_audio)
    FileKindUi.Document -> stringResource(R.string.picker_media_kind_document)
    FileKindUi.Folder, FileKindUi.Other -> stringResource(R.string.picker_media_kind_other)
    null -> title
}

private fun FileKindUi.icon(): ImageVector = when (this) {
    FileKindUi.Folder -> Icons.Default.Folder
    FileKindUi.Image -> Icons.Default.Image
    FileKindUi.Video -> Icons.Default.Movie
    FileKindUi.Audio -> Icons.Default.AudioFile
    FileKindUi.Document -> Icons.Default.Description
    FileKindUi.Other -> Icons.AutoMirrored.Filled.InsertDriveFile
}
