package com.fserver.app.presentation.screens.discovery.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardTitle
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.NetworkCardUi
import com.fserver.app.presentation.screens.discovery.shared.NetworkCard
import com.fserver.app.presentation.screens.discovery.hub.model.ConnectHubState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectHubScreen(
    viewModel: ConnectHubViewModel = koinViewModel(),
    navigateToNetworkSearch: () -> Unit,
    navigateToQrScan: () -> Unit,
    navigateToManualAddress: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ConnectHubScreen(
        state = state,
        navigateToNetworkSearch = navigateToNetworkSearch,
        navigateToQrScan = navigateToQrScan,
        navigateToManualAddress = navigateToManualAddress,
    )
}

@Composable
private fun ConnectHubScreen(
    state: ConnectHubState,
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

            RouteCard(
                title = stringResource(R.string.connect_find_title),
                description = stringResource(R.string.connect_find_description),
                onClick = navigateToNetworkSearch,
            )
            RouteCard(
                title = stringResource(R.string.action_scan_qr),
                description = stringResource(R.string.connect_scan_description),
                onClick = navigateToQrScan,
            )
            RouteCard(
                title = stringResource(R.string.action_enter_address),
                description = stringResource(R.string.connect_manual_description),
                onClick = navigateToManualAddress,
            )

            Spacer(Modifier.weight(1f))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.connect_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One way of reaching a server. The three are peers — none is drawn as the lesser path. */
@Composable
private fun RouteCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DkCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
            ) {
                DkCardTitle(text = title)
                DkCaption(text = description)
            }
            DkIcon(
                modifier = Modifier.padding(start = DkSpacing.sm),
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
            navigateToNetworkSearch = {},
            navigateToQrScan = {},
            navigateToManualAddress = {},
        )
    }
}
