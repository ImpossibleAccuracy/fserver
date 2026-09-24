package com.fserver.app.presentation.shared.browser

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi

/** Cloud mark on a file whose bytes are only on another device; nothing otherwise. */
@Composable
fun RemoteOnlyBadge(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
) {
    if (!file.isRemoteOnly) return

    DkIcon(
        modifier = modifier,
        icon = Icons.Default.Cloud,
        size = 14.dp,
        contentDescription = stringResource(R.string.file_location_remote),
    )
}
