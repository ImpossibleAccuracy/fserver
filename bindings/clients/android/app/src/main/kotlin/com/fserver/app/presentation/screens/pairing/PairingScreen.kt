package com.fserver.app.presentation.screens.pairing

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardKicker
import com.fserver.app.presentation.designkit.DkCardMeta
import com.fserver.app.presentation.designkit.DkCardTitle
import com.fserver.app.presentation.designkit.DkFingerprintBlock
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSegmentedControl
import com.fserver.app.presentation.designkit.DkSegmentedOption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.pairing.model.PairingIntent
import com.fserver.app.presentation.screens.pairing.model.PairingState
import com.fserver.app.presentation.screens.pairing.model.PairingUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.DeviceKind
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PairingScreen(
    key: Destination.Pairing,
    viewModel: PairingViewModel = koinViewModel { parametersOf(key) },
    navigateNext: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is PairingUiEffect.NavigateNext -> navigateNext()
            }
        }
    }

    PairingScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

@Composable
private fun PairingScreenContent(
    state: PairingState,
    onIntent: (PairingIntent) -> Unit,
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
            PairingUnavailable(
                error = state.error,
                modifier = Modifier.padding(innerPadding),
            )
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

                if (device.offeredMethods.isNotEmpty()) {
                    AuthMethodPicker(
                        offeredMethods = device.offeredMethods,
                        selectedMethod = device.selectedMethod,
                        onSelect = { onIntent(PairingIntent.MethodSelected(it)) },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
                    Text(
                        text = stringResource(R.string.pairing_fingerprint_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (device.selectedMethod) {
                        AuthMethod.NearbySas,
                        AuthMethod.ConfirmFingerprint -> {
                            if (device.fingerprintGroups.isNotEmpty()) {
                                DkFingerprintBlock(groups = device.fingerprintGroups)
                                Text(
                                    text = stringResource(R.string.pairing_fingerprint_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.pairing_fingerprint_pending),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        AuthMethod.Password -> {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = state.password ?: "",
                                onValueChange = {
                                    onIntent(PairingIntent.UpdatePassword(it))
                                }
                            )
                        }

                        null -> {}
                    }
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

                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
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
                    text = stringResource(
                        if (device.selectedMethod == AuthMethod.NearbySas) {
                            R.string.pairing_confirm_sas
                        } else {
                            R.string.pairing_confirm
                        }
                    ),
                    onClick = {
                        onIntent(PairingIntent.Connect)
                    },
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
 * Everything known about the device so far, in one block. Before a session exists that may be
 * only the greeting's protocol/method list — a manual address or QR code has no confirmed name
 * or kind until the handshake actually completes.
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
            DkThumbnail(icon = device.identity?.kind.icon)
            Column {
                DkCardTitle(
                    device.identity?.name ?: stringResource(R.string.pairing_unknown_device)
                )
                DkCardMeta(
                    device.protocolLine.ifEmpty { stringResource(R.string.pairing_protocol_pending) }
                )
            }
        }
        if (device.address != null) {
            Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.xs)) {
                CardFact(stringResource(R.string.pairing_address, device.address))
            }
        }
    }
}

/** Lets the user pick which of the device's offered methods to authenticate with. */
@Composable
private fun AuthMethodPicker(
    offeredMethods: List<AuthMethod>,
    selectedMethod: AuthMethod?,
    onSelect: (AuthMethod) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
        Text(
            text = stringResource(R.string.pairing_method_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (offeredMethods.size > 1) {
            DkSegmentedControl(
                options = offeredMethods.map { DkSegmentedOption(value = it, label = it.label) },
                selected = selectedMethod ?: offeredMethods.first(),
                onSelect = onSelect,
            )
        } else {
            CardFact((selectedMethod ?: offeredMethods.first()).label)
        }
    }
}

/** Friendly name for the method. */
private val AuthMethod.label: String
    @Composable get() = when (this) {
        AuthMethod.ConfirmFingerprint -> stringResource(R.string.pairing_method_confirm_fingerprint)
        AuthMethod.NearbySas -> stringResource(R.string.pairing_method_nearby_sas)
        AuthMethod.Password -> stringResource(R.string.pairing_method_password)
    }

@Composable
private fun CardFact(text: String) {
    Text(
        text = text,
        style = DkType.mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Nothing to confirm yet: either the device is still being reached, or reaching it failed. Both
 * land here, because a card built from half a handshake would invite the user to trust it.
 */
@Composable
private fun PairingUnavailable(error: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            if (error == null) {
                DkInlineSpinner()
                Text(
                    text = stringResource(R.string.pairing_connecting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.pairing_error_unreachable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PairingScreenPreview() {
    FServerTheme {
        PairingScreenContent(
            state = PairingState(device = PairingState.SampleDevice),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Multiple methods offered", showBackground = true)
@Composable
private fun PairingScreenMultiMethodPreview() {
    FServerTheme {
        PairingScreenContent(
            state = PairingState(
                device = PairingState.SampleDevice.copy(
                    identity = PairingState.DeviceUi.IdentityUi(
                        name = "HOME-NAS",
                        kind = DeviceKind.Nas,
                    ),
                    address = "192.168.1.42:8384",
                    offeredMethods = listOf(AuthMethod.ConfirmFingerprint, AuthMethod.NearbySas),
                    selectedMethod = AuthMethod.NearbySas,
                    fingerprintGroups = emptyList(),
                ),
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Manual address, unresolved", showBackground = true)
@Composable
private fun PairingScreenUnresolvedPreview() {
    FServerTheme {
        PairingScreenContent(
            state = PairingState(
                device = PairingState.DeviceUi(
                    identity = null,
                    address = "192.168.1.42:8384",
                    protocolLine = "protocol v1",
                    offeredMethods = listOf(AuthMethod.ConfirmFingerprint),
                    selectedMethod = AuthMethod.ConfirmFingerprint,
                    fingerprintGroups = emptyList(),
                ),
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Looking up", showBackground = true)
@Composable
private fun PairingScreenLoadingPreview() {
    FServerTheme {
        PairingScreenContent(
            state = PairingState(),
            onIntent = {},
            navigateUp = {},
        )
    }
}
