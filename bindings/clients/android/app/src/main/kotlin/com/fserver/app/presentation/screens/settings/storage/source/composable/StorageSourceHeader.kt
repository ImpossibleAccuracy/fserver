package com.fserver.app.presentation.screens.settings.storage.source.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkSegmentedControl
import com.fserver.app.presentation.designkit.DkSegmentedOption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.composable.LinkDirectionIcons
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.SortUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

/** How much one link holds here, where, and the controls that order the list below. */
@Composable
fun StorageSourceHeader(
    modifier: Modifier = Modifier,
    state: StorageSourceState,
    onSortChange: (SortUi) -> Unit,
    onGroupedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = FileSize(state.totalBytes).formatted(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            state.path?.let {
                DkMonoCaption(
                    modifier = Modifier.padding(start = DkSpacing.sm, bottom = DkSpacing.xs),
                    text = it,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
        ) {
            DkCaption(
                text = pluralStringResource(R.plurals.storage_files, state.files.size, state.files.size),
            )
            LinkDirectionIcons(direction = state.direction, deviceKind = state.peer.kind)
            DkCaption(text = state.peer.name)
        }

        if (!state.isEmpty) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SortMenu(sort = state.sort, onSelect = onSortChange)
                if (state.hasFolders) {
                    DkSegmentedControl(
                        options = listOf(
                            DkSegmentedOption(false, stringResource(R.string.storage_view_files)),
                            DkSegmentedOption(true, stringResource(R.string.storage_view_folders)),
                        ),
                        selected = state.grouped,
                        onSelect = onGroupedChange,
                        compact = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    modifier: Modifier = Modifier,
    sort: SortUi,
    onSelect: (SortUi) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(start = DkSpacing.sm, end = DkSpacing.xs),
        ) {
            Text(text = stringResource(sort.labelRes), style = MaterialTheme.typography.labelLarge)
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            SortUi.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (option == sort) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private val SortUi.labelRes: Int
    get() = when (this) {
        SortUi.Size -> R.string.storage_sort_size
        SortUi.Date -> R.string.storage_sort_date
        SortUi.Name -> R.string.storage_sort_name
    }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StorageSourceHeaderPreview() {
    FServerTheme {
        StorageSourceHeader(
            modifier = Modifier.padding(DkSpacing.screenPadding),
            state = StorageSourceState.Sample,
            onSortChange = {},
            onGroupedChange = {},
        )
    }
}
