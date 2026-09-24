package com.fserver.app.presentation.screens.settings.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.settings.devices.model.DevicesState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.device.model.DeviceKind
import org.koin.androidx.compose.koinViewModel

@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel = koinViewModel(),
    navigateToDevice: (String) -> Unit,
    navigateToConnect: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DevicesScreen(
        state = state,
        navigateToDevice = navigateToDevice,
        navigateToConnect = navigateToConnect,
        navigateUp = navigateUp,
    )
}

/**
 * The former "trusted key fingerprints" list, folded into the devices it belongs to. A key on its
 * own is not something a user can act on; the machine it belongs to is.
 *
 * Both sections are the same row, because they are the same kind of thing — the heading above them
 * is what distinguishes a live session from a remembered one. An empty section is dropped whole:
 * a heading with a count of zero is a heading about nothing.
 */
@Composable
private fun DevicesScreen(
    state: DevicesState,
    navigateToDevice: (String) -> Unit,
    navigateToConnect: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.devices_title),
                onBack = navigateUp,
            )
        },
        bottomBar = {
            DkPrimaryButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(DkSpacing.screenPadding),
                text = stringResource(R.string.devices_connect_new),
                onClick = navigateToConnect,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            DeviceSection(
                title = stringResource(R.string.devices_section_connected),
                devices = state.connected,
                onDeviceClick = navigateToDevice,
            )

            DeviceSection(
                title = stringResource(R.string.devices_section_trusted),
                devices = state.trusted,
                onDeviceClick = navigateToDevice,
            )

            if (state.isEmpty) {
                DkCaption(
                    modifier = Modifier.padding(DkSpacing.screenPadding),
                    text = stringResource(R.string.devices_empty),
                )
            }
        }
    }
}

@Composable
private fun DeviceSection(
    title: String,
    devices: List<DevicesState.DeviceUi>,
    onDeviceClick: (String) -> Unit,
) {
    if (devices.isEmpty()) return

    DkSectionLabel(
        modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
        text = title,
        trailing = { DkCaption(text = devices.size.toString()) },
    )

    devices.forEachIndexed { index, device ->
        DkListRow(
            title = device.name,
            subtitle = device.subtitle,
            leading = { DkThumbnail(icon = device.kind.icon) },
            trailing = { DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight) },
            onClick = { onDeviceClick(device.deviceId) },
        )
        if (index != devices.lastIndex) DkFadingDivider()
    }
}

@Preview(showBackground = true)
@Composable
private fun DevicesScreenPreview() {
    FServerTheme {
        DevicesScreen(
            state = DevicesState(
                connected = listOf(
                    DevicesState.DeviceUi(
                        deviceId = "1",
                        name = "MacBook-Pro.local",
                        subtitle = "192.168.1.14:8384",
                        kind = DeviceKind.Laptop,
                    ),
                    DevicesState.DeviceUi(
                        deviceId = "2",
                        name = "HOME-NAS",
                        subtitle = "nas.local:8384",
                        kind = DeviceKind.Nas,
                    ),
                ),
                trusted = listOf(
                    DevicesState.DeviceUi(
                        deviceId = "3",
                        name = "STUDIO-PC",
                        subtitle = null,
                        kind = DeviceKind.Desktop,
                    ),
                ),
            ),
            navigateToDevice = {},
            navigateToConnect = {},
            navigateUp = {},
        )
    }
}
