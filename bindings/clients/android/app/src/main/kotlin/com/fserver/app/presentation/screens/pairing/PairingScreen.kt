package com.fserver.app.presentation.screens.pairing

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.core.domain.model.DeviceConnectionCapabilities
import com.fserver.core.domain.model.FoundDevice
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardKicker
import com.fserver.app.presentation.designkit.DkCardMeta
import com.fserver.app.presentation.designkit.DkCardTitle
import com.fserver.app.presentation.designkit.DkFingerprintBlock
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTextField
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.model.icon
import com.fserver.app.presentation.screens.pairing.model.PairingIntent
import com.fserver.app.presentation.screens.pairing.model.PairingState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PairingScreen(
    deviceId: String,
    viewModel: PairingViewModel = koinViewModel { parametersOf(deviceId) },
    navigateToFiles: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PairingScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToFiles = navigateToFiles,
        navigateUp = navigateUp,
    )
}

@Composable
private fun PairingScreen(
    state: PairingState,
    onIntent: (PairingIntent) -> Unit,
    navigateToFiles: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.pairing_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        val device = state.device

        if (device == null) {
            PairingLoading(modifier = Modifier.padding(innerPadding))
            return@DkScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = DkSpacing.screenPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
            ) {
                DeviceCard(device = device)

                Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
                    Text(
                        text = stringResource(R.string.pairing_fingerprint_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DkFingerprintBlock(groups = device.fingerprintGroups)
                    Text(
                        text = stringResource(R.string.pairing_fingerprint_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.requiresPassword) {
                    DkTextField(
                        label = stringResource(R.string.pairing_password_label),
                        value = state.password,
                        onValueChange = { onIntent(PairingIntent.PasswordChanged(it)) },
                        isPassword = true,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
                ) {
                    Checkbox(
                        checked = state.rememberDevice,
                        onCheckedChange = { onIntent(PairingIntent.RememberDeviceChanged(it)) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.pairing_remember_device),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = DkSpacing.lg)
            )

            Column(
                modifier = Modifier.padding(bottom = DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm, Alignment.Bottom),
            ) {
                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.pairing_confirm),
                    onClick = navigateToFiles,
                    enabled = state.canConnect,
                )
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_cancel),
                    onClick = navigateUp,
                )
            }
        }
    }
}

/**
 * Everything the device asserted about itself, in the open and in one block — including the
 * access mode, so a server that will ask for nothing says so before the user connects
 * rather than after.
 */
@Composable
private fun DeviceCard(device: PairingState.DeviceUi) {
    DkCard {
        DkCardKicker(stringResource(R.string.pairing_kicker_server))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            DkThumbnail(icon = device.kind.icon)
            Column {
                DkCardTitle(device.name)
                DkCardMeta(device.technicalLine)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.xs)) {
            CardFact(stringResource(R.string.pairing_address, device.address))
            CardFact(
                stringResource(
                    when (device.access) {
                        DeviceConnectionCapabilities.Access.Open -> R.string.pairing_access_open
                        DeviceConnectionCapabilities.Access.Password -> R.string.pairing_access_password
                        DeviceConnectionCapabilities.Access.Key -> R.string.pairing_access_key
                    }
                )
            )
        }
    }
}

@Composable
private fun CardFact(text: String) {
    Text(
        text = text,
        style = DkType.mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PairingLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            DkInlineSpinner()
            Text(
                text = stringResource(R.string.pairing_connecting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@get:StringRes
private val DeviceConnectionCapabilities.Access.labelRes: Int
    get() = when (this) {
        DeviceConnectionCapabilities.Access.Open -> R.string.pairing_access_open
        DeviceConnectionCapabilities.Access.Password -> R.string.pairing_access_password
        DeviceConnectionCapabilities.Access.Key -> R.string.pairing_access_key
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun PairingScreenPreview() {
    FServerTheme {
        PairingScreen(
            state = PairingState(device = PairingState.SampleDevice),
            onIntent = {},
            navigateToFiles = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Password required", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun PairingScreenPasswordPreview() {
    FServerTheme {
        PairingScreen(
            state = PairingState(
                device = PairingState.SampleDevice.copy(
                    name = "HOME-NAS",
                    kind = FoundDevice.Kind.Nas,
                    access = DeviceConnectionCapabilities.Access.Password,
                    address = "192.168.1.42:8384",
                ),
            ),
            onIntent = {},
            navigateToFiles = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Looking up", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun PairingScreenLoadingPreview() {
    FServerTheme {
        PairingScreen(
            state = PairingState(),
            onIntent = {},
            navigateToFiles = {},
            navigateUp = {},
        )
    }
}
