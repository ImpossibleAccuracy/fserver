package com.fserver.app.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.composable.model.localizedName
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.device.IncomingConnection
import com.fserver.core.network.info.DetectionMethod


data class IncomingConnectionUi(
    val deviceName: String,
    val via: DetectionMethod?,
    val isUnsecured: Boolean,
)

fun IncomingConnection.toUi(): IncomingConnectionUi = IncomingConnectionUi(
    deviceName = deviceName,
    via = transport,
    // Neither a fingerprint nor digits: nothing here proves which device this is.
    isUnsecured = !isSecured,
)

/**
 * Another device is knocking, and its handshake is parked on this answer.
 *
 * What the user compares depends on how far the connection got before it needed permission: a key
 * fingerprint if the handshake completed, the transport's digits if it could not run yet. Either
 * way the decision is a comparison against the other screen, never a bare yes/no.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingConnectionSheet(
    request: IncomingConnectionUi,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        // Dismissing decides nothing, so it declines: a link left hanging is worse than a no.
        onDismissRequest = onDecline,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DkSpacing.lg)
                .padding(bottom = DkSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.incoming_connection_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Text(
                text = stringResource(
                    R.string.incoming_connection_from,
                    request.deviceName,
                    request.via?.localizedName?.let { stringResource(it) }
                        ?: stringResource(R.string.incoming_connection_unknown_transport),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (request.isUnsecured) {
                Text(
                    text = stringResource(R.string.incoming_connection_unverified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm, Alignment.End),
            ) {
                DkGhostButton(text = stringResource(R.string.action_decline), onClick = onDecline)
                DkPrimaryButton(text = stringResource(R.string.action_accept), onClick = onAccept)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun IncomingConnectionSheetPreview() {
    FServerTheme {
        IncomingConnectionSheet(
            request = IncomingConnectionUi(
                deviceName = "Alice's laptop",
                via = DetectionMethod.Automatic.MulticastDns,
                isUnsecured = false,
            ),
            onAccept = {},
            onDecline = {},
        )
    }
}
