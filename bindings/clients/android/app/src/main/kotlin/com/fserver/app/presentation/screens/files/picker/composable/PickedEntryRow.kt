package com.fserver.app.presentation.screens.files.picker.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState

@Composable
fun PickedEntryRow(entry: FilesPickerState.PickedEntryUi) {
    DkListRow(
        title = entry.name,
        /*subtitle = entry.path,
        subtitleStyle = DkType.mono,
        subtitleMaxLines = 3,*/
        leading = { DkThumbnail(icon = entry.kind.icon()) },
        trailing = entry.detailLabel?.let { detail -> { DkCaption(text = detail) } },
    )
}

private fun FileKindUi.icon(): ImageVector = when (this) {
    FileKindUi.Folder -> Icons.Default.Folder
    FileKindUi.Image -> Icons.Default.Image
    FileKindUi.Video -> Icons.Default.Movie
    FileKindUi.Audio -> Icons.Default.AudioFile
    FileKindUi.Document -> Icons.Default.Description
    FileKindUi.Other -> Icons.AutoMirrored.Filled.InsertDriveFile
}
