package com.fserver.app.presentation.screens.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardKicker
import com.fserver.app.presentation.designkit.DkCardMeta
import com.fserver.app.presentation.designkit.DkCardTitle
import com.fserver.app.presentation.designkit.DkFingerprintBlock
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.data.SampleData
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

/**
 * Trust on first connection.
 *
 * The fingerprint comparison is the only thing standing between the user and a
 * substituted server, so it gets the whole screen: nothing here may look like a formality
 * to tap past. Confirmation is an explicit statement ("fingerprints match"), never a bare
 * "OK".
 */
@Composable
private fun PairingScreen(
    state: PairingState,
    onIntent: (PairingIntent) -> Unit,
    navigateToFiles: () -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.pairing_title),
                onBack = navigateUp,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.padding(DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkPrimaryButton(
                    text = stringResource(R.string.pairing_confirm),
                    onClick = navigateToFiles,
                    modifier = Modifier.fillMaxWidth(),
                )
                DkGhostButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = navigateUp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
        ) {
            DkCard {
                DkCardKicker(stringResource(R.string.pairing_kicker_server))
                DkCardTitle(state.candidate.deviceName)
                DkCardMeta(state.candidate.technicalLine)
            }

            Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
                Text(
                    text = stringResource(R.string.pairing_fingerprint_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DkFingerprintBlock(groups = state.candidate.fingerprintGroups)
                Text(
                    text = stringResource(R.string.pairing_fingerprint_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun PairingScreenPreview() {
    FServerTheme {
        PairingScreen(
            state = PairingState(candidate = SampleData.pairingCandidate),
            onIntent = {},
            navigateToFiles = {},
            navigateUp = {},
        )
    }
}
