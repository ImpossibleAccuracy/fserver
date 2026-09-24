package com.fserver.app.presentation.shared.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.app.presentation.composable.model.FileKindUi


fun FileKindUi.icon(): ImageVector = when (this) {
    FileKindUi.Folder -> Icons.Default.Folder
    FileKindUi.Image -> Icons.Default.Image
    FileKindUi.Video -> Icons.Default.Movie
    FileKindUi.Audio -> Icons.Default.AudioFile
    FileKindUi.Document -> Icons.Default.Description
    FileKindUi.Other -> Icons.AutoMirrored.Filled.InsertDriveFile
}
