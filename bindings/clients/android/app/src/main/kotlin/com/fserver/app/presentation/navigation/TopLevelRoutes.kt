package com.fserver.app.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.app.R
import com.fserver.app.presentation.model.Destination

enum class TopLevelDestination(
    val destination: Destination,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Files(
        destination = Destination.Files.List,
        label = R.string.tab_files,
        icon = Icons.Default.Folder,
    ),
    Activity(
        destination = Destination.Activity,
        label = R.string.tab_activity,
        icon = Icons.Default.Timeline,
    ),
    Settings(
        destination = Destination.Settings,
        label = R.string.tab_settings,
        icon = Icons.Default.Settings,
    ),
}
