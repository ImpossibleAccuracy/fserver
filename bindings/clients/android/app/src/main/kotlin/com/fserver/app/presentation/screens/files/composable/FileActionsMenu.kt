package com.fserver.app.presentation.screens.files.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.screens.files.model.FilesState
import com.fserver.app.presentation.theme.FServerTheme

/** Three dots opening what can be done to a file, or to a selection. */
@Composable
fun FileActionsMenu(
    modifier: Modifier = Modifier,
    actions: Set<FilesState.FileActionUi>,
    onAction: (FilesState.FileActionUi) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        DkIconButton(
            onClick = { expanded = true },
            icon = Icons.Default.MoreVert,
            enabled = actions.isNotEmpty(),
            contentDescription = stringResource(R.string.files_file_actions),
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            FilesState.FileActionUi.entries.filter { it in actions }.forEach { action ->
                val color = if (action == FilesState.FileActionUi.Delete) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                DropdownMenuItem(
                    text = { Text(text = stringResource(action.labelRes), color = color) },
                    leadingIcon = {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = color,
                        )
                    },
                    onClick = {
                        expanded = false
                        onAction(action)
                    },
                )
            }
        }
    }
}

private val FilesState.FileActionUi.labelRes: Int
    get() = when (this) {
        FilesState.FileActionUi.Edit -> R.string.action_edit
        FilesState.FileActionUi.Rename -> R.string.files_action_rename
        FilesState.FileActionUi.Delete -> R.string.action_delete
    }

private val FilesState.FileActionUi.icon: ImageVector
    get() = when (this) {
        FilesState.FileActionUi.Edit -> Icons.Default.Edit
        FilesState.FileActionUi.Rename -> Icons.Default.DriveFileRenameOutline
        FilesState.FileActionUi.Delete -> Icons.Default.DeleteOutline
    }

@Preview(showBackground = true)
@Composable
private fun FileActionsMenuPreview() {
    FServerTheme {
        FileActionsMenu(actions = FilesState.FileActionUi.entries.toSet(), onAction = {})
    }
}
