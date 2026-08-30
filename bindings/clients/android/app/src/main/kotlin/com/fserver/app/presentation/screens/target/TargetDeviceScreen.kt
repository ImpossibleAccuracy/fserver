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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.designkit.dkDashedBorder
import com.fserver.app.presentation.screens.target.model.TargetDeviceIntent
import com.fserver.app.presentation.screens.target.model.TargetDeviceState
import com.fserver.app.presentation.screens.target.model.TargetDeviceUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun TargetDeviceScreen(
    viewModel: TargetDeviceViewModel = koinViewModel(),
    navigateToConnect: () -> Unit,
    answer: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is TargetDeviceUiEffect.AnswerDevice -> answer(effect.deviceId)
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
 * The step where the target finally gets a name. It sits between the mode and that mode's
 * conditions, for every branch.
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
                title = stringResource(R.string.target_device_source_title),
                onBack = navigateUp,
            )
        },
        bottomBar = {
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
        },
    ) { innerPadding ->
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
                text = stringResource(R.string.target_device_source_body),
            )

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

@Preview(showBackground = true)
@Composable
private fun TargetDeviceScreenPreview() {
    FServerTheme {
        TargetDeviceScreenContent(
            state = TargetDeviceState(
                devices = TargetDeviceState.SampleDevices,
                selectedDeviceId = "home-nas",
            ),
            onIntent = {},
            navigateToConnect = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Nothing connected", showBackground = true)
@Composable
private fun TargetDeviceScreenEmptyPreview() {
    FServerTheme {
        TargetDeviceScreenContent(
            state = TargetDeviceState(),
            onIntent = {},
            navigateToConnect = {},
            navigateUp = {},
        )
    }
}
