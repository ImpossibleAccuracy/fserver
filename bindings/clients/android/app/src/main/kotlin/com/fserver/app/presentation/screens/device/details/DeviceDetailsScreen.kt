package com.fserver.app.presentation.screens.device.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardMeta
import com.fserver.app.presentation.designkit.DkCardTitle
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkFingerprintBlock
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkValueRow
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.device.details.model.DeviceDetailsIntent
import com.fserver.app.presentation.screens.device.details.model.DeviceDetailsState
import com.fserver.app.presentation.screens.device.details.model.DeviceDetailsUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.info.model.PeerLocator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DeviceDetailsScreen(
    key: Destination.Settings.DeviceDetails,
    viewModel: DeviceDetailsViewModel = koinViewModel { parametersOf(key) },
    navigatePairing: (PeerLocator) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                DeviceDetailsUiEffect.NavigateBack -> navigateUp()
                is DeviceDetailsUiEffect.NavigatePairing -> navigatePairing(effect.peer)
            }
        }
    }

    DeviceDetailsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

/**
 * What this device proved, and the one control that undoes it.
 *
 * Forgetting is the only way to withdraw trust, so it says what happens next rather than asking
 * for a confirmation the user would learn to dismiss.
 */
@Composable
private fun DeviceDetailsScreen(
    state: DeviceDetailsState,
    onIntent: (DeviceDetailsIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = state.name,
                onBack = navigateUp,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                when {
                    state.isConnected -> {
                        DkSecondaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.devices_disconnect),
                            onClick = { onIntent(DeviceDetailsIntent.DisconnectClicked) },
                        )
                    }

                    state.isTrusted -> {
                        DkCaption(text = stringResource(R.string.device_details_forget_hint))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
                        ) {
                            if (state.isRouteKnown) {
                                DkSecondaryButton(
                                    modifier = Modifier.weight(1f),
                                    text = stringResource(R.string.device_details_reconnect),
                                    onClick = { onIntent(DeviceDetailsIntent.Reconnect) },
                                )
                            }

                            DkSecondaryButton(
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.device_details_forget),
                                onClick = { onIntent(DeviceDetailsIntent.ForgetClicked) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        // Rows carry the gutter themselves; the card, the fingerprint block and the labels do not.
        val gutter = Modifier.padding(horizontal = DkSpacing.screenPadding)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            StatusCard(modifier = gutter, state = state, onIntent = onIntent)

            DkSectionLabel(
                modifier = gutter,
                text = stringResource(R.string.device_details_fingerprint),
            )

            if (state.fingerprintGroups.isEmpty()) {
                DkCaption(
                    modifier = gutter,
                    text = stringResource(R.string.device_details_fingerprint_missing),
                )
            } else {
                DkFingerprintBlock(modifier = gutter, groups = state.fingerprintGroups)
            }

            DkSectionLabel(
                modifier = gutter,
                text = stringResource(R.string.device_details_section_details),
            )

            val unknown = stringResource(R.string.device_details_unknown)

            DkValueRow(
                title = stringResource(R.string.device_details_last_seen),
                value = state.lastSeen ?: unknown,
            )
            DkFadingDivider()

            DkValueRow(
                title = stringResource(R.string.device_details_method),
                value = state.methodLabel?.let { stringResource(it) } ?: unknown,
            )
            DkFadingDivider()

            DkValueRow(
                title = stringResource(R.string.device_details_protocol),
                value = state.protocolVersion?.let { "v$it" } ?: unknown,
            )
        }
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier = Modifier,
    state: DeviceDetailsState,
    onIntent: (DeviceDetailsIntent) -> Unit,
) {
    DkCard(modifier = modifier, outlined = state.isConnected) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = if (state.isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    ),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                DkCardTitle(
                    text = stringResource(
                        if (state.isConnected) {
                            R.string.device_details_connected
                        } else {
                            R.string.device_details_offline
                        }
                    ),
                )
                if (state.address != null) DkCardMeta(text = state.address)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceDetailsScreenPreview() {
    FServerTheme {
        DeviceDetailsScreen(
            state = DeviceDetailsState(
                name = "MacBook-Pro.local",
                isConnected = true,
                address = "192.168.1.14:8384",
                fingerprintGroups = listOf("9f2c 4a01", "b7d3 e820", "15aa cc94", "0f6b 7e31"),
                lastSeen = "12 Jun 2026",
                methodLabel = R.string.auth_method_confirm_fingerprint,
                protocolVersion = 1,
                isTrusted = true,
            ),
            onIntent = {},
            navigateUp = {},
        )
    }
}
