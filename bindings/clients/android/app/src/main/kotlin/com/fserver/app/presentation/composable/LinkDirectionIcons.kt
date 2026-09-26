package com.fserver.app.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.composable.model.LinkDirectionUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.device.model.DeviceKind

/** Which way a link goes and what kind of device is on the other end. Sits before the peer's name. */
@Composable
fun LinkDirectionIcons(
    modifier: Modifier = Modifier,
    direction: LinkDirectionUi,
    deviceKind: DeviceKind?,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
    ) {
        Icon(
            modifier = Modifier.size(IconSize),
            imageVector = direction.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            modifier = Modifier.size(IconSize),
            imageVector = deviceKind.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val IconSize = 14.dp

@Preview(showBackground = true)
@Composable
private fun LinkDirectionIconsPreview() {
    FServerTheme {
        LinkDirectionIcons(direction = LinkDirectionUi.Mirror, deviceKind = DeviceKind.Laptop)
    }
}
