package com.fserver.app.presentation.screens.qr

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.qr.model.QrScanState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun QrScanScreen(
    viewModel: QrScanViewModel = koinViewModel(),
    navigateToProfile: () -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    QrScanScreen(
        state = state,
        navigateToProfile = navigateToProfile,
        navigateToManualAddress = navigateToManualAddress,
        navigateUp = navigateUp,
    )
}

/**
 * QR path to a server. The code carries address, access method and key fingerprint at
 * once, which is why this flow skips the manual fingerprint confirmation the discovery
 * path requires — the comparison already happened, inside the code.
 *
 * The viewfinder is a placeholder: no camera is bound yet.
 */
@Composable
private fun QrScanScreen(
    state: QrScanState,
    navigateToProfile: () -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateUp: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.qr_title),
                onBack = navigateUp,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.padding(DkSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
            ) {
                Text(
                    text = stringResource(R.string.qr_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DkSecondaryButton(
                    text = stringResource(R.string.action_enter_address),
                    onClick = navigateToManualAddress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = DkSpacing.screenPadding),
        ) {
            if (!state.cameraActive) {
                DkPlaceholderBox(
                    label = stringResource(R.string.qr_camera_placeholder),
                    modifier = Modifier.fillMaxSize(),
                    dashedBorder = false,
                )
            }
            // Reticle: the frame the user aims with, and — until the camera is wired up —
            // the tap target that stands in for a recognised code.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(190.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable(onClick = navigateToProfile),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun QrScanScreenPreview() {
    FServerTheme {
        QrScanScreen(
            state = QrScanState(),
            navigateToProfile = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}
