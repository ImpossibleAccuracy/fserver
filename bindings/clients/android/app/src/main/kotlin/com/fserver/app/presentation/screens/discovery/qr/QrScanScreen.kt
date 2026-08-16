package com.fserver.app.presentation.screens.discovery.qr

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.discovery.qr.model.QrScanIntent
import com.fserver.app.presentation.screens.discovery.qr.model.QrScanState
import com.fserver.core.domain.model.network.PeerLocator
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun QrScanScreen(
    viewModel: QrScanViewModel = koinViewModel(),
    navigateToPairing: (target: PeerLocator) -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.found) {
        val target = state.found ?: return@LaunchedEffect
        viewModel.onIntent(QrScanIntent.ResultConsumed)
        navigateToPairing(target)
    }

    QrScanScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateToManualAddress = navigateToManualAddress,
        navigateUp = navigateUp,
    )
}

@Composable
private fun QrScanScreen(
    state: QrScanState,
    onIntent: (QrScanIntent) -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.qr_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = DkSpacing.screenPadding)
                .padding(bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                DkPlaceholderBox(
                    label = stringResource(R.string.qr_camera_placeholder),
                    modifier = Modifier.fillMaxSize(),
                    dashedBorder = false,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(190.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable(enabled = !state.isConnecting) {
                            onIntent(QrScanIntent.CodeScanned(SampleData.QR_PAYLOAD))
                        },
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
            ) {
                ScanStatus(state = state)
                DkSecondaryButton(
                    text = stringResource(R.string.action_enter_address),
                    onClick = navigateToManualAddress,
                    enabled = !state.isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The line under the viewfinder: what the scanner is doing, or why the last code led
 * nowhere. A failed scan says which of the two things went wrong — a code that made no
 * sense, or one that did and pointed at nothing — because the next move differs.
 */
@Composable
private fun ScanStatus(state: QrScanState) {
    when {
        state.isConnecting -> Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            DkInlineSpinner()
            Text(
                text = stringResource(R.string.qr_connecting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        state.error != null -> Text(
            text = when (state.error) {
                QrScanState.Error.MalformedCode -> stringResource(R.string.qr_error_malformed)
                QrScanState.Error.Unreachable -> stringResource(R.string.qr_error_unreachable)
                is QrScanState.Error.Unknown -> state.error.message ?: "Unknown error"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        else -> Text(
            text = stringResource(R.string.qr_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun QrScanScreenPreview() {
    FServerTheme {
        QrScanScreen(
            state = QrScanState(),
            onIntent = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Connecting", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun QrScanConnectingPreview() {
    FServerTheme {
        QrScanScreen(
            state = QrScanState(isConnecting = true),
            onIntent = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Unreadable code", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun QrScanErrorPreview() {
    FServerTheme {
        QrScanScreen(
            state = QrScanState(error = QrScanState.Error.MalformedCode),
            onIntent = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}
