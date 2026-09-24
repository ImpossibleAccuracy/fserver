package com.fserver.app.presentation.screens.dashboard

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.DkFabMenu
import com.fserver.app.presentation.composable.DkFabMenuItem
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.dashboard.composable.DeviceDetailsCard
import com.fserver.app.presentation.screens.dashboard.composable.DeviceStrip
import com.fserver.app.presentation.screens.dashboard.model.DashboardIntent
import com.fserver.app.presentation.screens.dashboard.model.DashboardState
import com.fserver.app.presentation.screens.source.request.shared.composable.SyncRequestBanner
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = koinViewModel(),
    navigateToFiles: () -> Unit,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: (String?) -> Unit,
    navigateToSyncRequests: () -> Unit,
    navigateToSourceDetails: (String) -> Unit,
    navigateToDeviceSettings: (String) -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateToQrScan: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DashboardScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToFiles = navigateToFiles,
        navigateToConnect = navigateToConnect,
        navigateToSourcePick = navigateToSourcePick,
        navigateToSyncRequests = navigateToSyncRequests,
        navigateToSourceDetails = navigateToSourceDetails,
        navigateToDeviceSettings = navigateToDeviceSettings,
        navigateToManualAddress = navigateToManualAddress,
        navigateToQrScan = navigateToQrScan,
    )
}

/** Devices, the way into their files, and the two ways to add more. */
@Composable
private fun DashboardScreenContent(
    modifier: Modifier = Modifier,
    state: DashboardState,
    onIntent: (DashboardIntent) -> Unit,
    navigateToFiles: () -> Unit,
    navigateToConnect: () -> Unit,
    navigateToSourcePick: (String?) -> Unit,
    navigateToSyncRequests: () -> Unit,
    navigateToSourceDetails: (String) -> Unit = {},
    navigateToDeviceSettings: (String) -> Unit = {},
    navigateToManualAddress: () -> Unit = {},
    navigateToQrScan: () -> Unit = {},
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                modifier = Modifier.alpha(if (state.expandedDevice != null) 0.35f else 1f),
                title = stringResource(R.string.dashboard_title),
            )
        },
        floatingActionButton = {
            DkFabMenu(
                items = listOf(
                    DkFabMenuItem(
                        icon = Icons.Default.SwapHoriz,
                        label = stringResource(R.string.fork_connect_title),
                        onClick = navigateToConnect,
                    ),
                    DkFabMenuItem(
                        icon = Icons.Default.Upload,
                        label = stringResource(R.string.fork_send_title),
                        onClick = { navigateToSourcePick(null) },
                    ),
                ),
                contentDescription = stringResource(R.string.files_actions),
                visible = state.expandedDevice == null,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Banners(
                state = state,
                onIntent = onIntent,
                navigateToSyncRequests = navigateToSyncRequests,
            )

            DkSectionLabel(
                modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                text = stringResource(R.string.dashboard_devices),
            )

            if (state.devices.isEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                    text = stringResource(R.string.files_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DeviceStrip(
                    modifier = Modifier.fillMaxWidth(),
                    devices = state.devices,
                    onDeviceClick = { onIntent(DashboardIntent.DeviceExpanded(it)) },
                )
            }

            DkListRow(
                modifier = Modifier.padding(top = DkSpacing.md),
                title = stringResource(R.string.dashboard_browse_files),
                subtitle = stringResource(R.string.dashboard_browse_files_desc),
                onClick = navigateToFiles,
                leading = { DkThumbnail(icon = Icons.Default.Folder) },
                trailing = { DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight) },
            )
        }
    }

    state.expandedDevice?.let { device ->
        Dialog(
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
            ),
            onDismissRequest = { onIntent(DashboardIntent.DeviceCollapsed) },
        ) {
            DeviceDetailsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DkSpacing.screenPadding),
                device = device,
                onClose = { onIntent(DashboardIntent.DeviceCollapsed) },
                onFolderClick = { navigateToSourceDetails(it.id) },
                onAddFolder = { navigateToSourcePick(device.id) },
                onConfigure = { navigateToDeviceSettings(device.id) },
                onReconnectByAddress = navigateToManualAddress,
                onReconnectByQr = navigateToQrScan,
            )
        }
    }
}

@Composable
private fun Banners(
    state: DashboardState,
    onIntent: (DashboardIntent) -> Unit,
    navigateToSyncRequests: () -> Unit,
) {
    // Held past the moment they clear, so each box has something to draw while it collapses.
    val warning = rememberLastNotNull(state.networkWarning)
    AnimatedVisibility(visible = state.networkWarning != null) {
        if (warning == null) return@AnimatedVisibility

        DkInfoBox(
            modifier = Modifier
                .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.sm)
                // Only the nameless-network one has a fix behind it; the rest are read-only.
                .then(
                    if (warning.isActionable) {
                        Modifier.clickable { onIntent(DashboardIntent.NetworkWarningClicked) }
                    } else {
                        Modifier
                    }
                ),
            text = stringResource(warning.messageRes),
        )
    }

    val syncRequest = rememberLastNotNull(state.syncRequest)
    AnimatedVisibility(visible = state.showsSyncRequestHint) {
        if (syncRequest == null) return@AnimatedVisibility

        SyncRequestBanner(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.sm),
            request = syncRequest,
            waiting = state.syncRequestsWaiting,
            onClick = navigateToSyncRequests,
            onDismiss = { onIntent(DashboardIntent.SyncRequestHintDismissed) },
        )
    }
}

/** [value], or the last non-null one it had: what an exit animation keeps drawing. */
@Composable
private fun <T : Any> rememberLastNotNull(value: T?): T? {
    val last = remember { LastValue<T>() }
    if (value != null) last.value = value
    return last.value
}

private class LastValue<T : Any> {
    var value: T? = null
}

@get:StringRes
private val DashboardState.NetworkWarningUi.messageRes: Int
    get() = when (this) {
        DashboardState.NetworkWarningUi.NoNetwork -> R.string.files_network_offline
        DashboardState.NetworkWarningUi.NoLocalNetwork -> R.string.files_network_no_lan
        DashboardState.NetworkWarningUi.DifferentNetwork -> R.string.files_network_other
        DashboardState.NetworkWarningUi.UnnamedNetwork -> R.string.files_network_unnamed
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DashboardScreenPreview() {
    FServerTheme {
        DashboardScreenContent(
            state = DashboardState(devices = DashboardState.SampleDevices),
            onIntent = {},
            navigateToFiles = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
        )
    }
}

@Preview(name = "Device expanded", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DashboardScreenExpandedPreview() {
    FServerTheme {
        DashboardScreenContent(
            state = DashboardState(
                devices = DashboardState.SampleDevices,
                expandedDevice = DashboardState.sampleDetailsOf(DashboardState.SampleDevices[1]),
                networkWarning = DashboardState.NetworkWarningUi.DifferentNetwork,
            ),
            onIntent = {},
            navigateToFiles = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
        )
    }
}

@Preview(name = "Nothing connected", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DashboardScreenEmptyPreview() {
    FServerTheme {
        DashboardScreenContent(
            state = DashboardState(),
            onIntent = {},
            navigateToFiles = {},
            navigateToConnect = {},
            navigateToSourcePick = {},
            navigateToSyncRequests = {},
        )
    }
}
