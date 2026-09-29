package com.fserver.app.presentation.screens.discovery.connect

import androidx.compose.runtime.getValue
import com.fserver.app.presentation.composable.PeerRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.permission.RequirementResolver
import com.fserver.app.presentation.permission.rememberRequirementResolver
import com.fserver.app.presentation.screens.discovery.connect.composable.DiscoveryMethodsSheet
import com.fserver.app.presentation.screens.discovery.connect.composable.TransportKindSheet
import com.fserver.app.presentation.screens.discovery.connect.model.ConnectIntent
import com.fserver.app.presentation.screens.discovery.connect.model.ConnectState
import com.fserver.app.presentation.screens.discovery.connect.model.ConnectUiEffect
import com.fserver.app.presentation.screens.discovery.shared.NetworkCard
import com.fserver.app.presentation.screens.discovery.shared.NetworkCardUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.info.model.PeerLocator
import org.koin.androidx.compose.koinViewModel

/**
 * Picking a device, wherever the ask came from.
 *
 * The screen answers and leaves: [onDeviceSelected] fires with a device that has a session, and
 * what happens next is the caller's business — opening its files, or receiving a source. A device
 * that is not connected yet goes through pairing first and comes back here.
 */
@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel = koinViewModel(),
    onDeviceSelected: (deviceId: String) -> Unit,
    navigateToPairing: (PeerLocator) -> Unit,
    navigateToQrScan: () -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = rememberRequirementResolver(onResolved = viewModel::onResumed)

    // Permissions can be granted or revoked from outside the app, so the reports are re-read
    // every time this screen comes back rather than cached from when it was opened.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { viewModel.onPaused() }
    }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ConnectUiEffect.DeviceSelected -> onDeviceSelected(effect.deviceId)

                is ConnectUiEffect.NavigatePairing -> navigateToPairing(effect.peer)
            }
        }
    }

    ConnectScreenContent(
        state = state,
        resolver = resolver,
        onIntent = viewModel::onIntent,
        navigateToQrScan = navigateToQrScan,
        navigateToManualAddress = navigateToManualAddress,
        navigateUp = navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectScreenContent(
    state: ConnectState,
    resolver: RequirementResolver,
    onIntent: (ConnectIntent) -> Unit,
    navigateToQrScan: () -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blockPadding = Modifier
        .fillMaxWidth()
        .padding(top = DkSpacing.md)
        .padding(horizontal = DkSpacing.screenPadding)

    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.connect_title),
                onBack = navigateUp,
                // Always the icon, never a spinner in its place: the sheet behind it is how a
                // running search is stopped, so it cannot be the thing that disappears. What is
                // scanning is said at the foot of the list instead.
                actions = {
                    IconButton(onClick = { onIntent(ConnectIntent.MethodsClicked) }) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = stringResource(R.string.connect_methods_action),
                        )
                    }
                },
            )
        },
        bottomBar = {
            DkActionBar {
                Row(horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
                    DkGhostButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.action_enter_address),
                        onClick = navigateToManualAddress,
                    )
                    DkGhostButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.action_scan_qr),
                        onClick = navigateToQrScan,
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item(key = "network") {
                NetworkCard(
                    modifier = blockPadding,
                    network = state.network,
                    onFixClick = state.networkAction?.let { action ->
                        { resolver.resolve(action) }
                    },
                )
            }

            if (state.known.isNotEmpty()) {
                item(key = "known-label") {
                    DkSectionLabel(
                        modifier = blockPadding,
                        text = stringResource(R.string.connect_known_label),
                        count = state.known.size,
                    )
                }

                deviceRows(state.known) { device ->
                    KnownDeviceRow(
                        device = device,
                        onClick = { onIntent(ConnectIntent.KnownDeviceClicked(device.id)) },
                    )
                }
            }

            if (state.discovered.isNotEmpty()) {
                item(key = "discovered-label") {
                    DkSectionLabel(
                        modifier = blockPadding,
                        text = stringResource(R.string.connect_discovered_label),
                        count = state.discovered.size,
                    )
                }

                deviceRows(state.discovered) { device ->
                    DiscoveredDeviceRow(
                        device = device,
                        onClick = { onIntent(ConnectIntent.DiscoveredDeviceClicked(device.id)) },
                    )
                }
            }

            if (state.isScanning) {
                item(key = "searching") {
                    SearchingFooter(modifier = blockPadding)
                }
            } else if (state.isEmpty) {
                item(key = "empty") {
                    EmptyHint(modifier = blockPadding)
                }
            }
        }
    }

    if (state.isMethodsOpen) {
        ModalBottomSheet(
            onDismissRequest = { onIntent(ConnectIntent.MethodsDismissed) },
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            DiscoveryMethodsSheet(state = state, onIntent = onIntent)
        }
    }

    // Above the methods sheet rather than replacing it: granting a permission answers a row on
    // the list underneath, which the user goes straight back to ticking.
    if (state.methodSetup != null) {
        ModalBottomSheet(
            onDismissRequest = { onIntent(ConnectIntent.MethodSetupDismissed) },
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            TransportKindSheet(setup = state.methodSetup, resolver = resolver)
        }
    }
}

/** Rows of one section, hairline-separated the way every other grouped list in the app is. */
private fun LazyListScope.deviceRows(
    devices: List<ConnectState.DeviceUi>,
    row: @Composable (ConnectState.DeviceUi) -> Unit,
) {
    itemsIndexed(devices, key = { _, device -> device.id }) { index, device ->
        row(device)
        if (index != devices.lastIndex) {
            DkFadingDivider()
        }
    }
}

@Composable
private fun KnownDeviceRow(
    device: ConnectState.DeviceUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PeerRow(
        modifier = modifier,
        peer = device.peer,
        subtitle = device.address ?: stringResource(R.string.connect_reconnect_hint),
        subtitleStyle = if (device.address != null) DkType.mono else null,
        dimmed = !device.isConnected,
        onClick = if (device.isBusy) null else onClick,
        trailing = {
            when {
                device.isBusy -> DkInlineSpinner()

                device.isConnected -> DkTag(
                    text = stringResource(R.string.connect_tag_connected),
                    style = DkTagStyle.Accent,
                )

                else -> DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
            }
        },
    )
}

@Composable
private fun DiscoveredDeviceRow(
    device: ConnectState.DeviceUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PeerRow(
        modifier = modifier,
        peer = device.peer,
        subtitle = device.address ?: stringResource(R.string.connect_connect_hint),
        subtitleStyle = if (device.address != null) DkType.mono else null,
        dimmed = true,
        onClick = onClick,
    )
}

@Composable
private fun SearchingFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = DkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DkInlineSpinner()
        DkCaption(text = stringResource(R.string.connect_searching))
    }
}

@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    DkCaption(
        modifier = modifier.padding(top = DkSpacing.md),
        text = stringResource(R.string.connect_empty_hint),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ConnectScreenPreview() {
    FServerTheme {
        ConnectScreenContent(
            state = ConnectState(
                network = NetworkCardUi.Wifi(SampleData.NETWORK_NAME),
                known = ConnectState.SampleKnown,
                discovered = ConnectState.SampleDiscovered,
            ),
            resolver = rememberRequirementResolver { },
            onIntent = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Nothing found", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ConnectScreenEmptyPreview() {
    FServerTheme {
        ConnectScreenContent(
            state = ConnectState(network = NetworkCardUi.Wifi(name = null)),
            resolver = rememberRequirementResolver { },
            onIntent = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}
