package com.fserver.app.presentation.screens.discovery.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.composable.model.NetworkCardUi
import com.fserver.app.presentation.screens.discovery.hub.model.ConnectHubState
import com.fserver.app.presentation.screens.discovery.shared.ConnectRouteCard
import com.fserver.app.presentation.screens.discovery.shared.NetworkCard
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectHubScreen(
    viewModel: ConnectHubViewModel = koinViewModel(),
    navigateFiles: () -> Unit,
    navigateToNetworkSearch: () -> Unit,
    navigateToQrScan: () -> Unit,
    navigateToManualAddress: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ConnectHubScreen(
        state = state,
        navigateFiles = {
            viewModel.onSkip()
            navigateFiles()
        },
        navigateToNetworkSearch = navigateToNetworkSearch,
        navigateToQrScan = navigateToQrScan,
        navigateToManualAddress = navigateToManualAddress,
    )
}

@Composable
private fun ConnectHubScreen(
    state: ConnectHubState,
    navigateFiles: () -> Unit,
    navigateToNetworkSearch: () -> Unit,
    navigateToQrScan: () -> Unit,
    navigateToManualAddress: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { DkTopBar(title = stringResource(R.string.connect_title)) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DkSpacing.screenPadding)
                .padding(bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            DkCaption(text = stringResource(R.string.connect_intro))

            NetworkCard(network = state.network)

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

            Spacer(Modifier.weight(1f))

            DkGhostButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Skip",
                onClick = navigateFiles,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ConnectHubNamedPreview() {
    FServerTheme {
        ConnectHubScreen(
            state = ConnectHubState(network = NetworkCardUi.Wifi(SampleData.NETWORK_NAME)),
            navigateFiles = {},
            navigateToNetworkSearch = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
        )
    }
}

@Preview(name = "Network name withheld", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ConnectHubRedactedPreview() {
    FServerTheme {
        ConnectHubScreen(
            state = ConnectHubState(network = NetworkCardUi.Wifi(name = null)),
            navigateFiles = {},
            navigateToNetworkSearch = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
        )
    }
}
