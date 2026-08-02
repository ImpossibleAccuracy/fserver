package com.fserver.app.presentation.screens.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.model.DeviceUi
import com.fserver.app.presentation.screens.discovery.model.DeviceDiscoveryIntent
import com.fserver.app.presentation.screens.discovery.model.DeviceDiscoveryState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun DeviceDiscoveryScreen(
    viewModel: DeviceDiscoveryViewModel = koinViewModel(),
    navigateToPairing: (deviceId: String) -> Unit,
    navigateToQrScan: () -> Unit,
    navigateToManualAddress: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DeviceDiscoveryScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToPairing = navigateToPairing,
        navigateToQrScan = navigateToQrScan,
        navigateToManualAddress = navigateToManualAddress,
    )
}

/**
 * mDNS discovery. Devices that went offline stay in the list, greyed out — the user needs
 * to see that the device exists and is simply unreachable right now, not wonder whether
 * they ever paired it. Scanning never "finishes": the manual routes sit below the list so
 * a network that blocks multicast is never a dead end.
 */
@Composable
private fun DeviceDiscoveryScreen(
    state: DeviceDiscoveryState,
    onIntent: (DeviceDiscoveryIntent) -> Unit,
    navigateToPairing: (deviceId: String) -> Unit,
    navigateToQrScan: () -> Unit,
    navigateToManualAddress: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.discovery_title),
                actions = {
                    IconButton(onClick = { onIntent(DeviceDiscoveryIntent.RefreshClicked) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.padding(DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.discovery_not_listed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                DkSecondaryButton(
                    text = stringResource(R.string.action_scan_qr),
                    onClick = navigateToQrScan,
                    modifier = Modifier.fillMaxWidth(),
                )
                DkSecondaryButton(
                    text = stringResource(R.string.action_enter_address),
                    onClick = navigateToManualAddress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                DkMonoCaption(
                    text = stringResource(
                        R.string.discovery_network_summary,
                        state.networkName,
                        state.devices.size,
                    ),
                    modifier = Modifier.padding(
                        start = DkSpacing.screenPadding,
                        end = DkSpacing.screenPadding,
                        bottom = DkSpacing.sm,
                    ),
                )
            }

            items(state.devices, key = { it.id }) { device ->
                DeviceRow(
                    device = device,
                    onClick = { navigateToPairing(device.id) },
                )
                DkFadingDivider()
            }

            if (state.searching) {
                item { SearchingRow() }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceUi, onClick: () -> Unit) {
    DkListRow(
        title = device.name,
        subtitle = device.lastSeenLabel
            ?.let { stringResource(R.string.device_last_seen, it) }
            ?: device.address,
        subtitleStyle = DkType.mono,
        dimmed = !device.online,
        onClick = onClick.takeIf { device.online },
        leading = { DkThumbnail(icon = Icons.Default.Computer) },
        trailing = {
            if (device.online) {
                DkTag(stringResource(R.string.device_status_online), style = DkTagStyle.Accent)
            } else {
                DkTag(stringResource(R.string.device_status_offline), style = DkTagStyle.Neutral)
            }
        },
    )
}

@Composable
private fun SearchingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        DkInlineSpinner()
        Text(
            text = stringResource(R.string.discovery_searching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoveryScreenPreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                networkName = SampleData.NETWORK_NAME,
                devices = SampleData.devices,
            ),
            onIntent = {},
            navigateToPairing = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
        )
    }
}
