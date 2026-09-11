package com.fserver.app.presentation.screens.source.setup.target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.LocalSnackbarController
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.screens.source.setup.target.model.SourceTargetIntent
import com.fserver.app.presentation.screens.source.setup.target.model.SourceTargetState
import com.fserver.app.presentation.screens.source.setup.target.model.SourceTargetUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.info.model.PeerLocator

@Composable
fun SourceTargetScreen(
    modifier: Modifier = Modifier,
    handler: SourceTargetHandler,
    navigateToPairing: (PeerLocator) -> Unit,
    navigateNext: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by handler.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarController.current
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        handler.onResumed()
        onPauseOrDispose { handler.onPaused() }
    }

    LaunchedEffect(handler.effects) {
        handler.effects.collect { effect ->
            when (effect) {
                is SourceTargetUiEffect.NavigatePairing -> navigateToPairing(effect.peer)

                SourceTargetUiEffect.RouteUnknown ->
                    snackbar.showSnackbar(context.getString(R.string.source_target_route_unknown))

                is SourceTargetUiEffect.ReconnectFailed -> snackbar.showSnackbar(
                    effect.reason ?: context.getString(R.string.source_target_reconnect_failed)
                )
            }
        }
    }

    SourceTargetScreenContent(
        modifier = modifier,
        state = state,
        onIntent = handler::onIntent,
        navigateNext = {
            handler.onIntent(SourceTargetIntent.Confirmed)
            navigateNext()
        },
        navigateUp = navigateUp,
    )
}

@Composable
private fun SourceTargetScreenContent(
    modifier: Modifier = Modifier,
    state: SourceTargetState,
    onIntent: (SourceTargetIntent) -> Unit,
    navigateNext: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.source_target_title),
                onBack = navigateUp,
            )
        },
        bottomBar = {
            DkActionBar {
                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_continue),
                    enabled = state.canContinue,
                    onClick = navigateNext,
                )
            }
        },
    ) { innerPadding ->
        val blockPadding = Modifier.padding(horizontal = DkSpacing.screenPadding)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
        ) {
            if (state.connected.isNotEmpty()) {
                DkSectionLabel(
                    modifier = blockPadding,
                    text = stringResource(R.string.source_target_connected_label),
                    trailing = { DkCaption(text = state.connected.size.toString()) },
                )

                DeviceGroup(devices = state.connected) { device ->
                    ConnectedDeviceRow(
                        device = device,
                        selected = device.id == state.selectedDeviceId,
                        onSelect = { onIntent(SourceTargetIntent.DeviceSelected(device.id)) },
                    )
                }
            }

            if (state.known.isNotEmpty()) {
                DkSectionLabel(
                    modifier = blockPadding,
                    text = stringResource(R.string.source_target_known_label),
                    trailing = { DkCaption(text = state.known.size.toString()) },
                )

                DeviceGroup(devices = state.known) { device ->
                    OfflineDeviceRow(
                        device = device,
                        hint = stringResource(R.string.source_target_reconnect_hint),
                        onClick = { onIntent(SourceTargetIntent.ReconnectClicked(device.id)) },
                    )
                }
            }

            if (state.isSearching || state.isScanningSubnet) {
                SearchingFooter(
                    modifier = blockPadding,
                    text = stringResource(
                        if (state.isScanningSubnet) {
                            R.string.source_target_subnet_scanning
                        } else {
                            R.string.source_target_searching
                        }
                    ),
                )
            }

            if (!state.isScanningSubnet) {
                // TODO: show some kind of progress
                SubnetScanOffer(
                    modifier = blockPadding,
                    onScan = { onIntent(SourceTargetIntent.SubnetScanClicked) },
                )
            }

            if (state.discovered.isNotEmpty()) {
                DkSectionLabel(
                    modifier = blockPadding,
                    text = stringResource(R.string.source_target_discovered_label),
                    trailing = { DkCaption(text = state.discovered.size.toString()) },
                )

                DeviceGroup(devices = state.discovered) { device ->
                    OfflineDeviceRow(
                        device = device,
                        hint = stringResource(R.string.source_target_connect_hint),
                        onClick = {
                            onIntent(SourceTargetIntent.DiscoveredDeviceClicked(device.id))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceGroup(
    modifier: Modifier = Modifier,
    devices: List<SourceTargetState.DeviceUi>,
    row: @Composable (SourceTargetState.DeviceUi) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        devices.forEachIndexed { index, device ->
            row(device)
            if (index != devices.lastIndex) {
                DkFadingDivider()
            }
        }
    }
}

@Composable
private fun ConnectedDeviceRow(
    modifier: Modifier = Modifier,
    device: SourceTargetState.DeviceUi,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    DkListRow(
        modifier = modifier,
        title = device.name,
        subtitle = device.address,
        subtitleStyle = DkType.mono,
        onClick = onSelect,
        leading = { DkThumbnail(icon = device.kind.icon) },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DkTag(
                    text = stringResource(R.string.source_target_tag_connected),
                    style = DkTagStyle.Accent,
                )
                RadioButton(selected = selected, onClick = onSelect)
            }
        },
    )
}

@Composable
private fun OfflineDeviceRow(
    modifier: Modifier = Modifier,
    device: SourceTargetState.DeviceUi,
    hint: String,
    onClick: () -> Unit,
) {
    DkListRow(
        modifier = modifier,
        title = device.name,
        subtitle = device.address ?: hint,
        subtitleStyle = if (device.address != null) DkType.mono else null,
        dimmed = true,
        onClick = if (device.isBusy) null else onClick,
        leading = { DkThumbnail(icon = device.kind.icon) },
        trailing = {
            if (device.isBusy) {
                DkInlineSpinner()
            } else {
                DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
            }
        },
    )
}

@Composable
private fun SubnetScanOffer(modifier: Modifier = Modifier, onScan: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DkCaption(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.source_target_subnet_offer_hint),
        )
        DkGhostButton(
            text = stringResource(R.string.source_target_subnet_offer_action),
            onClick = onScan,
        )
    }
}

@Composable
private fun SearchingFooter(modifier: Modifier = Modifier, text: String) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = DkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DkInlineSpinner()
        DkCaption(text = text)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceTargetScreenPreview() {
    FServerTheme {
        SourceTargetScreenContent(
            state = SourceTargetState(
                connected = SourceTargetState.SampleConnected,
                known = SourceTargetState.SampleKnown,
                discovered = SourceTargetState.SampleDiscovered,
                selectedDeviceId = "home-nas",
                isSearching = true,
            ),
            onIntent = {},
            navigateNext = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Nothing found", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SourceTargetScreenEmptyPreview() {
    FServerTheme {
        SourceTargetScreenContent(
            state = SourceTargetState(),
            onIntent = {},
            navigateNext = {},
            navigateUp = {},
        )
    }
}
