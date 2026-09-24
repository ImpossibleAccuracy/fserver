package com.fserver.app.presentation.screens.dashboard.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.dashboard.model.DashboardState
import com.fserver.app.presentation.theme.FServerTheme

private val StatusDot = 5.dp

/** Every device with a folder, as one row. A tap opens the device's card in place. */
@Composable
fun DeviceStrip(
    modifier: Modifier = Modifier,
    devices: List<DashboardState.DeviceUi>,
    onDeviceClick: (String) -> Unit,
) {
    when {
        devices.isEmpty() -> {}

        devices.size <= 3 -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = DkSpacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                devices.forEach { device ->
                    DeviceCard(
                        modifier = Modifier.weight(1f, fill = false),
                        device = device,
                        onClick = { onDeviceClick(device.id) },
                    )
                }
            }
        }

        else -> {
            LazyRow(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = DkSpacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    modifier: Modifier = Modifier,
    device: DashboardState.DeviceUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium

    // A device nothing could reach is the one thing on this row the user has to act on.
    val accent = if (device.unreachable) colors.error else colors.outline

    Column(
        modifier = modifier
            .widthIn(min = 100.dp, max = 200.dp)
            .clip(shape)
            .border(1.dp, accent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = DkSpacing.md, vertical = DkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = device.kind.icon,
            contentDescription = null,
            tint = if (device.unreachable) colors.error else colors.onSurfaceVariant,
        )
        Text(
            text = device.name,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(StatusDot)
                    .clip(CircleShape)
                    .background(
                        when {
                            device.unreachable -> colors.error
                            device.online -> colors.primary
                            else -> colors.outline
                        }
                    )
            )
            Text(
                text = stringResource(
                    when {
                        device.unreachable -> R.string.files_device_unreachable_state
                        device.online -> R.string.device_state_online
                        else -> R.string.device_state_offline
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (device.unreachable) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DeviceStripPreview() {
    FServerTheme {
        DeviceStrip(
            devices = DashboardState.SampleDevices,
            onDeviceClick = {},
        )
    }
}
