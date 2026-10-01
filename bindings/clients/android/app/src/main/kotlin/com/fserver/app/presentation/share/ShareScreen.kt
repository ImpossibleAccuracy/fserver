package com.fserver.app.presentation.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.PeerRow
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.share.model.ShareState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.device.model.DeviceKind

@Composable
fun ShareScreen(viewModel: ShareViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ShareScreen(state = state, onDeviceClick = viewModel::send, onClose = onClose)
}

/** One tap on a device sends. Nothing else to decide: the receiving side picks where files go. */
@Composable
private fun ShareScreen(
    state: ShareState,
    onDeviceClick: (String) -> Unit,
    onClose: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = pluralStringResource(R.plurals.share_title, state.fileCount, state.fileCount),
                onBack = onClose,
                backIcon = Icons.Default.Close,
                backLabel = stringResource(R.string.action_close),
            )
        },
    ) { innerPadding ->
        if (state.sending) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.md, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DkInlineSpinner(modifier = Modifier.size(32.dp))
                Text(
                    text = stringResource(R.string.share_copying),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@DkScaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            state.error?.let { error ->
                item {
                    DkInfoBox(
                        modifier = Modifier.padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.sm),
                        title = error.detail?.let { error.message.asString() },
                        text = (error.detail ?: error.message).asString(),
                    )
                }
            }

            if (state.devices.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(DkSpacing.screenPadding)) {
                        Text(
                            text = stringResource(R.string.share_no_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@LazyColumn
            }

            item {
                DkSectionLabel(
                    modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                    text = stringResource(R.string.share_send_to),
                )
            }

            items(state.devices, key = { it.id }) { peer ->
                PeerRow(
                    peer = peer,
                    subtitle = stringResource(if (peer.online) R.string.share_device_online else R.string.share_device_offline),
                    dimmed = !peer.online,
                    onClick = { onDeviceClick(peer.id) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShareScreenPreview() {
    FServerTheme {
        ShareScreen(
            state = ShareState(
                fileCount = 3,
                devices = listOf(
                    PeerUi("laptop", "Laptop", DeviceKind.Laptop, online = true),
                    PeerUi("pc", "Home PC", DeviceKind.Desktop),
                ),
            ),
            onDeviceClick = {},
            onClose = {},
        )
    }
}
