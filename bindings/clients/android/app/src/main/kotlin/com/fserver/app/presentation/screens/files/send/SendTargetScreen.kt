package com.fserver.app.presentation.screens.files.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.LocalSnackbarController
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.designkit.dkDashedBorder
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.screens.discovery.shared.ConnectRouteCard
import com.fserver.app.presentation.screens.files.send.model.SendTargetIntent
import com.fserver.app.presentation.screens.files.send.model.SendTargetState
import com.fserver.app.presentation.screens.files.send.model.SendTargetUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SendTargetScreen(
    key: Destination.Files.SendTarget,
    viewModel: SendTargetViewModel = koinViewModel { parametersOf(key) },
    navigateToNetworkSearch: () -> Unit,
    navigateToQrScan: () -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateToFiles: () -> Unit,
    navigateUp: () -> Unit,
) {
    val snackbar = LocalSnackbarController.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                SendTargetUiEffect.NavigateFinished -> navigateToFiles()
                is SendTargetUiEffect.ShowMessage -> {
                    snackbar.showSnackbar(effect.message)
                }
            }
        }
    }

    // Leaving for a route is announced, so the device paired out there is recognised as the one
    // this screen was waiting for when it comes back.
    val openRoute = { route: () -> Unit ->
        viewModel.onIntent(SendTargetIntent.ConnectRouteOpened)
        route()
    }

    SendTargetScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToNetworkSearch = { openRoute(navigateToNetworkSearch) },
        navigateToQrScan = { openRoute(navigateToQrScan) },
        navigateToManualAddress = { openRoute(navigateToManualAddress) },
        navigateUp = navigateUp,
    )
}

/**
 * Connected devices first, then the same three ways of reaching a new one the connect hub
 * offers — a device has to be paired before it can receive anything, so finding one is part of
 * choosing where to send.
 */
@Composable
private fun SendTargetScreenContent(
    state: SendTargetState,
    onIntent: (SendTargetIntent) -> Unit,
    navigateToNetworkSearch: () -> Unit,
    navigateToQrScan: () -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.send_target_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        if (state.isSelectionLost) {
            SelectionLost(
                onBack = navigateUp,
                modifier = Modifier.padding(innerPadding),
            )
            return@DkScaffold
        }

        // Only the blocks outside the list get the screen padding: [DkListRow] brings its own,
        // so its touch target keeps running the full width.
        val blockPadding = Modifier.padding(horizontal = DkSpacing.screenPadding)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = DkSpacing.screenPadding),
        ) {
            DkCaption(
                modifier = blockPadding,
                text = pluralStringResource(
                    R.plurals.picker_selected_count,
                    state.fileCount,
                    state.fileCount,
                )
            )
            if (state.previewLine.isNotEmpty()) {
                DkMonoCaption(
                    modifier = blockPadding.padding(top = DkSpacing.xxs),
                    text = state.previewLine,
                )
            }

            DkSectionLabel(
                modifier = blockPadding,
                text = stringResource(R.string.send_target_devices_label),
                trailing = { DkCaption(text = state.devices.size.toString()) },
            )

            if (state.devices.isEmpty()) {
                NoConnectedDevices(modifier = blockPadding)
            } else {
                state.devices.forEachIndexed { index, device ->
                    DkListRow(
                        title = device.name,
                        subtitle = device.address,
                        subtitleStyle = DkType.mono,
                        onClick = { onIntent(SendTargetIntent.DeviceSelected(device.id)) },
                        leading = { DkThumbnail(icon = device.kind.icon) },
                        trailing = { DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight) },
                    )
                    if (index != state.devices.lastIndex) {
                        DkFadingDivider()
                    }
                }
            }

            DkSectionLabel(
                modifier = blockPadding,
                text = stringResource(R.string.send_target_routes_label),
            )

            Column(
                modifier = blockPadding,
                verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
            ) {
                ConnectRouteCard(
                    title = stringResource(R.string.connect_find_title),
                    description = stringResource(R.string.connect_find_description),
                    onClick = navigateToNetworkSearch,
                )
                ConnectRouteCard(
                    title = stringResource(R.string.action_scan_qr),
                    description = stringResource(R.string.connect_scan_description),
                    onClick = navigateToQrScan,
                )
                ConnectRouteCard(
                    title = stringResource(R.string.action_enter_address),
                    description = stringResource(R.string.connect_manual_description),
                    onClick = navigateToManualAddress,
                )
            }
        }

        state.confirmation?.let { confirmation ->
            SendConfirmationDialog(
                confirmation = confirmation,
                onConfirm = { onIntent(SendTargetIntent.SendConfirmed) },
                onCancel = { onIntent(SendTargetIntent.SendCancelled) },
            )
        }
    }
}

/** The last step before bytes move, so it names both the count and who receives them. */
@Composable
private fun SendConfirmationDialog(
    confirmation: SendTargetState.ConfirmationUi,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = pluralStringResource(
                    R.plurals.send_confirm_title,
                    confirmation.fileCount,
                    confirmation.fileCount,
                )
            )
        },
        text = {
            Text(
                text = stringResource(
                    R.string.send_confirm_body,
                    confirmation.deviceName,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            DkPrimaryButton(
                text = stringResource(R.string.action_send),
                onClick = onConfirm,
            )
        },
        dismissButton = {
            DkGhostButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
            )
        },
    )
}

/** Nothing is connected yet — the routes below are the way out, so this only says so. */
@Composable
private fun NoConnectedDevices(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .dkDashedBorder(MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
        ) {
            Text(
                text = stringResource(R.string.send_target_empty_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DkCaption(text = stringResource(R.string.send_target_empty_hint))
        }
    }
}

/**
 * The store is in memory, so a selection can outlive nothing but the process. Sending a
 * half-remembered list would be worse than asking for it again.
 */
@Composable
private fun SelectionLost(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(DkSpacing.screenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.send_target_selection_lost),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            DkGhostButton(
                text = stringResource(R.string.action_back),
                onClick = onBack,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SendTargetScreenPreview() {
    FServerTheme {
        SendTargetScreenContent(
            state = SendTargetState(
                fileCount = 4,
                fileNames = listOf("Shoot", "IMG_4831.RAW", "interview_02.wav", "estimate.pdf"),
                devices = SendTargetState.SampleDevices,
            ),
            onIntent = {},
            navigateToNetworkSearch = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Nothing connected", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SendTargetScreenEmptyPreview() {
    FServerTheme {
        SendTargetScreenContent(
            state = SendTargetState(
                fileCount = 1,
                fileNames = listOf("estimate_final.pdf"),
            ),
            onIntent = {},
            navigateToNetworkSearch = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Confirming", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SendTargetScreenConfirmPreview() {
    FServerTheme {
        SendTargetScreenContent(
            state = SendTargetState(
                fileCount = 4,
                fileNames = listOf("Shoot", "IMG_4831.RAW", "interview_02.wav", "estimate.pdf"),
                devices = SendTargetState.SampleDevices,
                confirmation = SendTargetState.ConfirmationUi(
                    deviceId = "home-nas",
                    deviceName = "HOME-NAS",
                    fileCount = 4,
                ),
            ),
            onIntent = {},
            navigateToNetworkSearch = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}
