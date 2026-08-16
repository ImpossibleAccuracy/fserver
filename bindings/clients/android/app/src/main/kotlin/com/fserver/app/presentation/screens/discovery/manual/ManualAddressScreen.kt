package com.fserver.app.presentation.screens.discovery.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
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
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTextField
import com.fserver.app.presentation.screens.discovery.manual.model.ManualAddressIntent
import com.fserver.app.presentation.screens.discovery.manual.model.ManualAddressState
import com.fserver.core.domain.model.network.PeerLocator
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ManualAddressScreen(
    viewModel: ManualAddressViewModel = koinViewModel(),
    navigateToPairing: (target: PeerLocator) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.found) {
        val target = state.found ?: return@LaunchedEffect
        viewModel.onIntent(ManualAddressIntent.ResultConsumed)
        navigateToPairing(target)
    }

    ManualAddressScreen(
        state = state,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun ManualAddressScreen(
    state: ManualAddressState,
    onIntent: (ManualAddressIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        DkSectionLabel(text = stringResource(R.string.manual_title))

        DkTextField(
            label = stringResource(R.string.manual_host_label),
            value = state.host,
            onValueChange = { onIntent(ManualAddressIntent.HostChanged(it)) },
        )

        DkTextField(
            label = stringResource(R.string.manual_port_label),
            value = state.port,
            onValueChange = { onIntent(ManualAddressIntent.PortChanged(it)) },
        )

        if (state.error != null) {
            Text(
                text = when (state.error) {
                    ManualAddressState.Error.InvalidPort -> stringResource(R.string.manual_error_port)
                    ManualAddressState.Error.Unreachable -> stringResource(R.string.manual_error_unreachable)
                    is ManualAddressState.Error.Unknown -> state.error.message ?: "Unknown error"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(
                text = stringResource(R.string.manual_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isChecking) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
            ) {
                DkInlineSpinner()
                Text(
                    text = stringResource(R.string.manual_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DkPrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.manual_connect),
            enabled = state.canConnect,
            onClick = { onIntent(ManualAddressIntent.ConnectClicked) },
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ManualAddressScreenPreview() {
    FServerTheme {
        ManualAddressScreen(
            state = ManualAddressState(host = "192.168.1.42", port = "8384"),
            onIntent = {},
        )
    }
}

@Preview(name = "Checking", showBackground = true, widthDp = 360)
@Composable
private fun ManualAddressCheckingPreview() {
    FServerTheme {
        ManualAddressScreen(
            state = ManualAddressState(host = "192.168.1.42", isChecking = true),
            onIntent = {},
        )
    }
}

@Preview(name = "Unreachable", showBackground = true, widthDp = 360)
@Composable
private fun ManualAddressUnreachablePreview() {
    FServerTheme {
        ManualAddressScreen(
            state = ManualAddressState(
                host = "10.0.0.5",
                error = ManualAddressState.Error.Unreachable,
            ),
            onIntent = {},
        )
    }
}
