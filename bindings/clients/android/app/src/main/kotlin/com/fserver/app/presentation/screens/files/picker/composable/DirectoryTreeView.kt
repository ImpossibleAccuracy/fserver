package com.fserver.app.presentation.screens.files.picker.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTreeRow
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState

/** Bounded so the sheet keeps a sane height whatever the tree turns out to hold. */
private val TreeMaxHeight = 420.dp

/**
 * Full-access browsing: the whole device as one tree, opened at the storage roots and listed
 * a level at a time. Only directories are rows here — this source picks folders to send, so a
 * file would be a row that cannot be selected.
 *
 * Tapping a row folds it open or shut; the checkbox is the selection, and the two are kept
 * apart on purpose so opening a folder never picks it.
 */
@Composable
fun DirectoryTreeView(
    nodes: List<FilesPickerState.TreeNodeUi>,
    onExpandToggle: (String) -> Unit,
    onSelectionToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) {
        DkMonoCaption(
            modifier = modifier.padding(DkSpacing.xl),
            text = stringResource(R.string.picker_tree_empty),
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = TreeMaxHeight)
            .padding(horizontal = DkSpacing.md),
    ) {
        item {
            DkMonoCaption(
                modifier = Modifier.padding(
                    horizontal = DkSpacing.sm,
                    vertical = DkSpacing.xs,
                ),
                text = stringResource(R.string.picker_tree_hint),
            )
        }

        items(nodes, key = { it.id }) { node ->
            DkTreeRow(
                title = node.name,
                depth = node.depth,
                expandable = node.expandable,
                expanded = node.expanded,
                trailingText = node.detailLabel,
                onClick = { onExpandToggle(node.id) },
                trailing = {
                    Checkbox(
                        checked = node.selected,
                        onCheckedChange = { onSelectionToggle(node.id) },
                        modifier = Modifier.size(24.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                },
            )
        }
    }
}
