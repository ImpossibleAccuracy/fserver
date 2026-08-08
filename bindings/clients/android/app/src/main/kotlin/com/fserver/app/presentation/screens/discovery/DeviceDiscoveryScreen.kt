package com.fserver.app.presentation.screens.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.fserver.app.domain.model.DetectionMethod
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
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
 * Device discovery. Devices that went offline stay in the list, greyed out — the user needs
 * to see that the device exists and is simply unreachable right now, not wonder whether
 * they ever paired it.
 *
 * The empty states are never a dead end: the summary line says what the current transport
 * can and cannot do, and the manual routes sit below the body throughout, so a network
 * that blocks multicast still leads somewhere.
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NetworkSummary(
                modifier = Modifier.padding(
                    start = DkSpacing.screenPadding,
                    end = DkSpacing.screenPadding,
                    bottom = DkSpacing.sm,
                ),
                network = state.network,
                deviceCount = state.devices.size,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.devices.isEmpty() -> {
                        if (state.isSearching) {
                            DiscoveryEmptyState(
                                title = stringResource(R.string.discovery_searching_title),
                                hint = stringResource(R.string.discovery_searching_hint),
                                showSpinner = true,
                            )
                        } else {
                            DiscoveryEmptyState(
                                title = stringResource(R.string.discovery_empty_title),
                                hint = stringResource(R.string.discovery_searching_hint),
                            )
                        }
                    }

                    else -> {
                        DeviceList(
                            devices = state.devices,
                            searching = state.isSearching,
                            onDeviceClick = navigateToPairing,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DkSpacing.screenPadding)
                    .padding(bottom = DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                state.detectionMethods.forEach { methodUi ->
                    when (methodUi.method) {
                        is DetectionMethod.Automatic -> return@forEach

                        DetectionMethod.OnDemand.ManualAddress -> {
                            DkSecondaryButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(R.string.action_scan_qr),
                                onClick = navigateToQrScan,
                            )

                            DkSecondaryButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(R.string.action_enter_address),
                                onClick = navigateToManualAddress,
                            )
                        }

                        DetectionMethod.OnDemand.SubnetScan -> {
                            DkPrimaryButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !methodUi.isSearching,
                                text = stringResource(R.string.discovery_scan_subnet),
                                onClick = { onIntent(DeviceDiscoveryIntent.ScanSubnetClicked) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Names the transport and, when it limits discovery, says so — an empty list on mobile
 * data is a property of the network, not a failure the user should keep retrying.
 */
@Composable
private fun NetworkSummary(
    modifier: Modifier = Modifier,
    network: DeviceDiscoveryState.NetworkInfoUi?,
    deviceCount: Int,
) {
    val summary = when (network?.type) {
        DeviceDiscoveryState.NetworkInfoUi.Type.WiFi ->
            stringResource(R.string.discovery_network_summary_wifi, network.name, deviceCount)

        DeviceDiscoveryState.NetworkInfoUi.Type.Mobile ->
            stringResource(R.string.discovery_network_summary_mobile, network.name, deviceCount)

        null -> stringResource(R.string.discovery_network_summary_offline, deviceCount)
    }

    val hint = when (network?.type) {
        DeviceDiscoveryState.NetworkInfoUi.Type.WiFi -> null
        DeviceDiscoveryState.NetworkInfoUi.Type.Mobile ->
            stringResource(R.string.discovery_network_hint_mobile)

        null -> stringResource(R.string.discovery_network_hint_offline)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        DkMonoCaption(text = summary)
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<DeviceDiscoveryState.DeviceUi>,
    searching: Boolean,
    onDeviceClick: (deviceId: String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(devices, key = { it.id }) { device ->
            DeviceRow(
                device = device,
                onClick = { onDeviceClick(device.id) },
            )
            DkFadingDivider()
        }

        if (searching) {
            item { SearchingRow() }
        }
    }
}

@Composable
private fun DiscoveryEmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    showSpinner: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = DkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) {
            DkInlineSpinner()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceRow(device: DeviceDiscoveryState.DeviceUi, onClick: () -> Unit) {
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
                network = DeviceDiscoveryState.NetworkInfoUi(
                    name = SampleData.NETWORK_NAME,
                    type = DeviceDiscoveryState.NetworkInfoUi.Type.WiFi
                ),
                devices = DeviceDiscoveryState.SampleDevices,
            ),
            onIntent = {},
            navigateToPairing = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
        )
    }
}

@Preview(name = "Searching", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoverySearchingPreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                network = DeviceDiscoveryState.NetworkInfoUi(
                    name = SampleData.NETWORK_NAME,
                    type = DeviceDiscoveryState.NetworkInfoUi.Type.WiFi
                ),
                detectionMethods = listOf(
                    DeviceDiscoveryState.DetectionMethodUi(
                        method = DetectionMethod.Automatic.MulticastDns,
                        isSearching = true,
                    )
                )
            ),
            onIntent = {},
            navigateToPairing = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
        )
    }
}

@Preview(name = "Nothing found", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoveryNothingFoundPreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                network = DeviceDiscoveryState.NetworkInfoUi(
                    name = SampleData.NETWORK_NAME,
                    type = DeviceDiscoveryState.NetworkInfoUi.Type.WiFi
                ),
                detectionMethods = listOf(
                    DeviceDiscoveryState.DetectionMethodUi(
                        method = DetectionMethod.Automatic.MulticastDns,
                        isSearching = true,
                    )
                )
            ),
            onIntent = {},
            navigateToPairing = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
        )
    }
}

@Preview(name = "Subnet scanning", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoverySubnetScanningPreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                network = DeviceDiscoveryState.NetworkInfoUi(
                    name = SampleData.NETWORK_NAME,
                    type = DeviceDiscoveryState.NetworkInfoUi.Type.WiFi
                ),
                detectionMethods = listOf(
                    DeviceDiscoveryState.DetectionMethodUi(
                        method = DetectionMethod.OnDemand.SubnetScan,
                        isSearching = true,
                    ),
                    DeviceDiscoveryState.DetectionMethodUi(
                        method = DetectionMethod.OnDemand.ManualAddress,
                        isSearching = false,
                    ),
                )
            ),
            onIntent = {},
            navigateToPairing = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
        )
    }
}

@Preview(name = "Nothing found — exhausted", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoveryExhaustedPreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                network = DeviceDiscoveryState.NetworkInfoUi(
                    name = SampleData.NETWORK_NAME,
                    type = DeviceDiscoveryState.NetworkInfoUi.Type.WiFi
                ),
                detectionMethods = listOf(
                    DeviceDiscoveryState.DetectionMethodUi(
                        method = DetectionMethod.OnDemand.SubnetScan,
                        isSearching = false,
                    ),
                    DeviceDiscoveryState.DetectionMethodUi(
                        method = DetectionMethod.OnDemand.ManualAddress,
                        isSearching = false,
                    ),
                )
            ),
            onIntent = {},
            navigateToPairing = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
        )
    }
}

@Preview(name = "Mobile network", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DeviceDiscoveryMobilePreview() {
    FServerTheme {
        DeviceDiscoveryScreen(
            state = DeviceDiscoveryState(
                network = DeviceDiscoveryState.NetworkInfoUi(
                    name = "LTE",
                    type = DeviceDiscoveryState.NetworkInfoUi.Type.Mobile,
                ),
            ),
            onIntent = {},
            navigateToPairing = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
        )
    }
}
