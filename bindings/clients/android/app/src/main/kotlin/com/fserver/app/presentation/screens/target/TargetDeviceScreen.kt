package com.fserver.app.presentation.screens.target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.fserver.app.presentation.composable.model.SourceKindUi
import com.fserver.app.presentation.composable.model.SourceModeUi
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.designkit.dkDashedBorder
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.TargetPurpose
import com.fserver.app.presentation.screens.target.model.TargetDeviceIntent
import com.fserver.app.presentation.screens.target.model.TargetDeviceState
import com.fserver.app.presentation.screens.target.model.TargetDeviceUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TargetDeviceScreen(
    key: Destination.TargetDevice,
    viewModel: TargetDeviceViewModel = koinViewModel { parametersOf(key) },
    navigateToConnect: () -> Unit,
    navigateToConditions: () -> Unit,
    navigateToFiles: () -> Unit,
    navigateUp: () -> Unit,
) {
    val snackbar = LocalSnackbarController.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                TargetDeviceUiEffect.NavigateFinished -> navigateToFiles()
                TargetDeviceUiEffect.NavigateToConditions -> navigateToConditions()
                is TargetDeviceUiEffect.ShowMessage -> {
                    snackbar.showSnackbar(effect.message)
                }
            }
        }
    }

    TargetDeviceScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        // Leaving to add a device is announced, so whoever is paired out there is recognised as
        // the one this screen was waiting for when it comes back.
        navigateToConnect = {
            viewModel.onIntent(TargetDeviceIntent.ConnectRouteOpened)
            navigateToConnect()
        },
        navigateUp = navigateUp,
    )
}

/**
 * Screen 5d of the send flow, and the last step of the picker flow — the same question in both.
 *
 * One list of connected devices, single choice, committed by an explicit continue. A known
 * device that is offline stays in the list, dimmed and unpickable: dropping it would read as
 * the app having forgotten it.
 */
@Composable
private fun TargetDeviceScreenContent(
    state: TargetDeviceState,
    onIntent: (TargetDeviceIntent) -> Unit,
    navigateToConnect: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(
                    if (state.isConfiguringSource) {
                        R.string.target_device_source_title
                    } else {
                        R.string.target_device_title
                    }
                ),
                onBack = navigateUp,
            )
        },
        bottomBar = {
            if (!state.isSelectionLost) {
                DkActionBar {
                    DkPrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_continue),
                        enabled = state.canContinue,
                        onClick = { onIntent(TargetDeviceIntent.ContinueClicked) },
                    )
                    DkSecondaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.target_device_add_device),
                        onClick = navigateToConnect,
                    )
                }
            }
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
            if (state.isConfiguringSource) {
                DkCaption(
                    modifier = blockPadding,
                    text = stringResource(R.string.target_device_source_body),
                )
            } else {
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
            }

            DkSectionLabel(
                modifier = blockPadding,
                text = stringResource(R.string.target_device_devices_label),
                trailing = { DkCaption(text = state.devices.size.toString()) },
            )

            if (state.devices.isEmpty()) {
                NoConnectedDevices(modifier = blockPadding)
            } else {
                state.devices.forEachIndexed { index, device ->
                    DeviceRow(
                        device = device,
                        selected = device.id == state.selectedDeviceId,
                        onSelect = { onIntent(TargetDeviceIntent.DeviceSelected(device.id)) },
                    )
                    if (index != state.devices.lastIndex) {
                        DkFadingDivider()
                    }
                }
            }
        }

        state.confirmation?.let { confirmation ->
            SendConfirmationDialog(
                confirmation = confirmation,
                onConfirm = { onIntent(TargetDeviceIntent.SendConfirmed) },
                onCancel = { onIntent(TargetDeviceIntent.SendCancelled) },
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: TargetDeviceState.DeviceUi,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DkListRow(
        modifier = modifier,
        title = device.name,
        subtitle = if (device.online) {
            device.address
        } else {
            stringResource(R.string.target_device_offline)
        },
        subtitleStyle = if (device.online) DkType.mono else null,
        dimmed = !device.online,
        onClick = if (device.online) onSelect else null,
        leading = { DkThumbnail(icon = device.kind.icon) },
        trailing = {
            RadioButton(
                selected = selected,
                onClick = if (device.online) onSelect else null,
                enabled = device.online,
            )
        },
    )
}

/** The last step before bytes move, so it names both the count and who receives them. */
@Composable
private fun SendConfirmationDialog(
    confirmation: TargetDeviceState.ConfirmationUi,
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

/** Nothing is connected yet — "add a device" below is the way out, so this only says so. */
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
                text = stringResource(R.string.target_device_empty_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DkCaption(text = stringResource(R.string.target_device_empty_hint))
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
                text = stringResource(R.string.target_device_selection_lost),
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

private val SendFilesPurpose = TargetPurpose.SendFiles(selectionId = "preview")

private val ConfigureSourcePurpose = TargetPurpose.ConfigureSource(
    kind = SourceKindUi.Photos,
    mode = SourceModeUi.AutoUpload,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun TargetDeviceScreenPreview() {
    FServerTheme {
        TargetDeviceScreenContent(
            state = TargetDeviceState(
                purpose = SendFilesPurpose,
                fileCount = 4,
                fileNames = listOf("Shoot", "IMG_4831.RAW", "interview_02.wav", "estimate.pdf"),
                devices = TargetDeviceState.SampleDevices,
                selectedDeviceId = "home-nas",
            ),
            onIntent = {},
            navigateToConnect = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Configuring a source", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun TargetDeviceScreenSourcePreview() {
    FServerTheme {
        TargetDeviceScreenContent(
            state = TargetDeviceState(
                purpose = ConfigureSourcePurpose,
                devices = TargetDeviceState.SampleDevices,
            ),
            onIntent = {},
            navigateToConnect = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Nothing connected", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun TargetDeviceScreenEmptyPreview() {
    FServerTheme {
        TargetDeviceScreenContent(
            state = TargetDeviceState(
                purpose = SendFilesPurpose,
                fileCount = 1,
                fileNames = listOf("estimate_final.pdf"),
            ),
            onIntent = {},
            navigateToConnect = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Confirming", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun TargetDeviceScreenConfirmPreview() {
    FServerTheme {
        TargetDeviceScreenContent(
            state = TargetDeviceState(
                purpose = SendFilesPurpose,
                fileCount = 4,
                fileNames = listOf("Shoot", "IMG_4831.RAW", "interview_02.wav", "estimate.pdf"),
                devices = TargetDeviceState.SampleDevices,
                selectedDeviceId = "home-nas",
                confirmation = TargetDeviceState.ConfirmationUi(
                    deviceId = "home-nas",
                    deviceName = "HOME-NAS",
                    fileCount = 4,
                ),
            ),
            onIntent = {},
            navigateToConnect = {},
            navigateUp = {},
        )
    }
}
