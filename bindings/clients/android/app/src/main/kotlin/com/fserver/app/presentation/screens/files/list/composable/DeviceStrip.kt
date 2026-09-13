package com.fserver.app.presentation.screens.files.list.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.files.list.model.FilesState
import com.fserver.app.presentation.theme.FServerTheme

private val StatusDot = 5.dp

/**
 * Every connected device as one scrolling row above the feed.
 *
 * A tap narrows the feed to that device; a long press opens it in place. Neither leaves the
 * screen, because the feed is the place where a device is a filter, not a destination.
 */
@Composable
fun DeviceStrip(
    modifier: Modifier = Modifier,
    devices: List<FilesState.DeviceUi>,
    selectedDeviceId: String?,
    onDeviceClick: (String) -> Unit,
    onDeviceLongClick: (String) -> Unit,
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
                        selected = device.id == selectedDeviceId,
                        onClick = { onDeviceClick(device.id) },
                        onLongClick = { onDeviceLongClick(device.id) },
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
                        selected = device.id == selectedDeviceId,
                        onClick = { onDeviceClick(device.id) },
                        onLongClick = { onDeviceLongClick(device.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceCard(
    modifier: Modifier = Modifier,
    device: FilesState.DeviceUi,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium

    Column(
        modifier = modifier
            .widthIn(min = 100.dp, max = 200.dp)
            .clip(shape)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) colors.primary else colors.outline, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = DkSpacing.md, vertical = DkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = device.kind.icon,
            contentDescription = null,
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
        )
        Text(
            text = device.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
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
                            selected -> colors.onPrimaryContainer.copy(
                                alpha = if (device.online) 1f else 0.2f
                            )

                            device.online -> colors.primary
                            else -> colors.outline
                        }
                    )
            )
            Text(
                text = stringResource(
                    if (device.online) R.string.device_state_online else R.string.device_state_offline
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
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
            devices = FilesState.SampleDevices,
            selectedDeviceId = "server",
            onDeviceClick = {},
            onDeviceLongClick = {},
        )
    }
}
