package com.fserver.app.presentation.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkThumbnail

/** One device in a list: kind icon, name, a line under it, a chevron unless [trailing] says otherwise. */
@Composable
fun PeerRow(
    modifier: Modifier = Modifier,
    peer: PeerUi,
    subtitle: String? = null,
    subtitleStyle: TextStyle? = null,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = { DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight) },
) {
    DkListRow(
        modifier = modifier,
        title = peer.name,
        subtitle = subtitle,
        subtitleStyle = subtitleStyle,
        dimmed = dimmed,
        onClick = onClick,
        leading = { DkThumbnail(icon = peer.kind.icon) },
        trailing = trailing,
    )
}
