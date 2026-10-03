package com.fserver.app.presentation.shared.browser.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi

/** Cloud mark on a file whose bytes are only on another device, pin on a pinned one; nothing otherwise. */
@Composable
fun FileStateBadge(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
) {
    val (icon, description) = when {
        file.isRemoteOnly -> Icons.Default.Cloud to R.string.file_location_remote
        file.isPinned -> Icons.Default.PushPin to R.string.files_state_pinned
        else -> return
    }

    DkIcon(
        modifier = modifier,
        icon = icon,
        size = 14.dp,
        contentDescription = stringResource(description),
    )
}
