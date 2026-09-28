package com.fserver.app.presentation.screens.settings.mydevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkFingerprintBlock
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkNavigationRow
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSettingsRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.composable.TextEditorDialog
import com.fserver.app.presentation.screens.settings.mydevice.composable.AddressRow
import com.fserver.app.presentation.screens.settings.mydevice.composable.ConnectionQrSheet
import com.fserver.app.presentation.screens.settings.mydevice.model.MyDeviceIntent
import com.fserver.app.presentation.screens.settings.mydevice.model.MyDeviceState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun MyDeviceScreen(
    viewModel: MyDeviceViewModel = koinViewModel(),
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MyDeviceScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

@Composable
private fun MyDeviceScreen(
    state: MyDeviceState,
    onIntent: (MyDeviceIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    var showInvitation by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }

    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.my_device_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            DkSettingsRow(
                title = stringResource(R.string.security_device_name),
                supportingText = state.name.ifEmpty {
                    stringResource(R.string.security_device_name_desc)
                },
                trailing = {
                    DkGhostButton(
                        text = stringResource(R.string.security_action_change),
                        onClick = { renaming = true },
                    )
                },
            )
            DkFadingDivider()

            DkNavigationRow(
                title = stringResource(R.string.devices_qr_action),
                supportingText = stringResource(R.string.my_device_qr_desc),
                onClick = { showInvitation = true },
            )

            (state.invitation as? MyDeviceState.InvitationUi.Ready)?.let {
                ConnectionDetails(invitation = it)
            }
        }
    }

    if (showInvitation) {
        ConnectionQrSheet(
            invitation = state.invitation,
            onDismiss = { showInvitation = false },
        )
    }

    if (renaming) {
        TextEditorDialog(
            title = stringResource(R.string.security_rename_title),
            label = stringResource(R.string.security_device_name),
            initialValue = state.name,
            onDismiss = { renaming = false },
            onConfirm = {
                onIntent(MyDeviceIntent.Renamed(it))
                renaming = false
            },
        )
    }
}

@Composable
private fun ConnectionDetails(
    modifier: Modifier = Modifier,
    invitation: MyDeviceState.InvitationUi.Ready,
) {
    Column(
        modifier = modifier.padding(horizontal = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        if (invitation.addresses.isNotEmpty()) {
            DkSectionLabel(text = stringResource(R.string.devices_qr_address_label))
            invitation.addresses.forEach { AddressRow(address = it) }
        }

        DkSectionLabel(text = stringResource(R.string.devices_qr_fingerprint_label))
        DkFingerprintBlock(groups = invitation.fingerprintGroups)
    }
}

@Preview(showBackground = true)
@Composable
private fun MyDeviceScreenPreview() {
    FServerTheme {
        MyDeviceScreen(
            state = MyDeviceState(name = "Pixel 8", invitation = MyDeviceState.SampleInvitation),
            onIntent = {},
            navigateUp = {},
        )
    }
}
