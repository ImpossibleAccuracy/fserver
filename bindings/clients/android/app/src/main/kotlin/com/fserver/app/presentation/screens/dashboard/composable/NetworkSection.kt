package com.fserver.app.presentation.screens.dashboard.composable

import com.fserver.app.presentation.designkit.DkStatusDot
import android.text.format.DateUtils
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.dashboard.model.DashboardState
import com.fserver.app.presentation.theme.FServerTheme


/** The link this phone is on, and every device a source is paired with. */
@Composable
fun NetworkSection(
    modifier: Modifier = Modifier,
    network: DashboardState.NetworkUi?,
    discovering: Boolean,
    devices: List<DashboardState.DeviceUi>,
    onlineDevices: Int,
    onConnect: () -> Unit,
    onDeviceClick: (String) -> Unit,
) {
    Column(modifier = modifier) {
        NetworkCard(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
            network = network,
            discovering = discovering,
            online = onlineDevices,
            total = devices.size,
            onConnect = onConnect,
        )

        Column(modifier = Modifier.padding(top = DkSpacing.sm)) {
            devices.forEachIndexed { index, device ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                DeviceRow(device = device, onClick = { onDeviceClick(device.peer.id) })
            }
        }
    }
}

/** What the section shows before any source exists: nothing to list, one way forward. */
@Composable
fun NetworkEmptyState(
    modifier: Modifier = Modifier,
    onConnect: () -> Unit,
) {
    Column(
        modifier = modifier.panel(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        NetworkBadge(icon = Icons.Default.Devices)
        Text(
            modifier = Modifier.padding(top = DkSpacing.xs),
            text = stringResource(R.string.dashboard_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.dashboard_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        DkPrimaryButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DkSpacing.sm),
            text = stringResource(R.string.fork_connect_title),
            onClick = onConnect,
        )
    }
}

@Composable
private fun NetworkCard(
    modifier: Modifier = Modifier,
    network: DashboardState.NetworkUi?,
    discovering: Boolean,
    online: Int,
    total: Int,
    onConnect: () -> Unit,
) {
    Row(
        modifier = modifier.panel(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        NetworkBadge(icon = network.icon, breathing = discovering)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
        ) {
            Text(
                text = network.title(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
            ) {
                DkStatusDot(
                    color = if (online > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
                Text(
                    text = stringResource(R.string.dashboard_network_online, online, total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DkGhostButton(
            text = stringResource(R.string.dashboard_network_connect),
            onClick = onConnect,
        )
    }
}

@Composable
private fun DeviceRow(
    device: DashboardState.DeviceUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val peer = device.peer
    val offline = !peer.online

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            imageVector = peer.kind.icon,
            contentDescription = null,
            tint = if (offline) colors.outline else colors.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = peer.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (offline) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DkStatusDot(
            color = when {
                device.unreachable -> colors.error
                peer.online -> colors.primary
                else -> colors.outline
            },
        )
        Text(
            text = when {
                peer.online -> stringResource(R.string.device_state_online)
                device.unreachable -> stringResource(R.string.files_device_unreachable_state)
                peer.lastSeen != null -> stringResource(
                    R.string.dashboard_device_last_seen,
                    peer.lastSeen.formatted(DateUtils.MINUTE_IN_MILLIS),
                )

                else -> stringResource(R.string.device_state_offline)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (device.unreachable) colors.error else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** With [breathing], a halo swells and fades around the badge: discovery is looking right now. */
@Composable
private fun NetworkBadge(icon: ImageVector, breathing: Boolean = false) {
    val halo = MaterialTheme.colorScheme.primary
    val breath = if (breathing) {
        val transition = rememberInfiniteTransition(label = "breath")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breath",
        )
    } else {
        null
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .drawBehind {
                val phase = breath?.value ?: return@drawBehind
                drawCircle(
                    color = halo.copy(alpha = 0.25f * (1f - phase) + 0.05f),
                    radius = size.minDimension / 2 * (1f + 0.25f * phase),
                )
            }
            .border(
                width = 1.dp,
                color = if (breathing) halo else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Modifier.panel(): Modifier {
    val shape = MaterialTheme.shapes.large

    return this
        .fillMaxWidth()
        .clip(shape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .padding(DkSpacing.lg)
}

private val DashboardState.NetworkUi?.icon: ImageVector
    get() = when (this?.kind) {
        null -> Icons.Default.WifiOff
        DashboardState.NetworkKindUi.WiFi -> Icons.Default.Wifi
        DashboardState.NetworkKindUi.Mobile -> Icons.Default.CellTower
        DashboardState.NetworkKindUi.Wired -> Icons.Default.Lan
        DashboardState.NetworkKindUi.Other -> Icons.Default.Public
    }

@Composable
private fun DashboardState.NetworkUi?.title(): String = this?.name ?: stringResource(
    when (this?.kind) {
        null -> R.string.dashboard_network_offline
        DashboardState.NetworkKindUi.WiFi -> R.string.dashboard_network_wifi
        DashboardState.NetworkKindUi.Mobile -> R.string.dashboard_network_mobile
        DashboardState.NetworkKindUi.Wired -> R.string.dashboard_network_wired
        DashboardState.NetworkKindUi.Other -> R.string.dashboard_network_other
    }
)

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun NetworkSectionPreview() {
    FServerTheme {
        NetworkSection(
            network = DashboardState.Sample.network,
            discovering = DashboardState.Sample.discovering,
            devices = DashboardState.Sample.devices,
            onlineDevices = DashboardState.Sample.onlineDevices,
            onConnect = {},
            onDeviceClick = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true, widthDp = 360)
@Composable
private fun NetworkEmptyStatePreview() {
    FServerTheme {
        NetworkEmptyState(
            modifier = Modifier.padding(DkSpacing.screenPadding),
            onConnect = {},
        )
    }
}
