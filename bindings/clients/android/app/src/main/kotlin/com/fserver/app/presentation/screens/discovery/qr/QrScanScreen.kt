package com.fserver.app.presentation.screens.discovery.qr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.permission.RequirementAction
import com.fserver.app.presentation.permission.rememberRequirementResolver
import com.fserver.app.presentation.screens.discovery.qr.composable.QrCameraPreview
import com.fserver.app.presentation.screens.discovery.qr.model.QrScanIntent
import com.fserver.app.presentation.screens.discovery.qr.model.QrScanState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.info.model.PeerLocator
import org.koin.androidx.compose.koinViewModel

@Composable
fun QrScanScreen(
    modifier: Modifier = Modifier,
    viewModel: QrScanViewModel = koinViewModel(),
    navigateToPairing: (target: PeerLocator) -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    val resolver = rememberRequirementResolver {
        hasCameraPermission = context.hasCameraPermission()
    }
    val requestCamera = {
        resolver.resolve(RequirementAction.RequestPermissions(listOf(Manifest.permission.CAMERA)))
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) requestCamera()
    }

    LaunchedEffect(state.found) {
        val target = state.found ?: return@LaunchedEffect
        viewModel.onIntent(QrScanIntent.ResultConsumed)
        navigateToPairing(target)
    }

    QrScanScreen(
        modifier = modifier,
        state = state,
        hasCameraPermission = hasCameraPermission,
        onIntent = viewModel::onIntent,
        onRequestCameraPermission = requestCamera,
        navigateToManualAddress = navigateToManualAddress,
        navigateUp = navigateUp,
    )
}

@Composable
private fun QrScanScreen(
    modifier: Modifier = Modifier,
    state: QrScanState,
    hasCameraPermission: Boolean,
    onIntent: (QrScanIntent) -> Unit,
    onRequestCameraPermission: () -> Unit,
    navigateToManualAddress: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
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
                if (hasCameraPermission) {
                    QrCameraPreview(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium),
                        isActive = !state.isConnecting,
                        onCode = { payload -> onIntent(QrScanIntent.CodeScanned(payload)) },
                    )
                } else {
                    DkPlaceholderBox(
                        modifier = Modifier.fillMaxSize(),
                        label = stringResource(R.string.qr_camera_off),
                        dashedBorder = false,
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(190.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp),
                        ),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
            ) {
                ScanStatus(
                    state = state,
                    hasCameraPermission = hasCameraPermission,
                )

                if (!hasCameraPermission) {
                    DkPrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.qr_action_grant_camera),
                        onClick = onRequestCameraPermission,
                    )
                }

                DkSecondaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_enter_address),
                    onClick = navigateToManualAddress,
                    enabled = !state.isConnecting,
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
private fun ScanStatus(
    modifier: Modifier = Modifier,
    state: QrScanState,
    hasCameraPermission: Boolean,
) {
    when {
        state.isConnecting -> Column(
            modifier = modifier.fillMaxWidth(),
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
            modifier = modifier,
            text = when (state.error) {
                QrScanState.Error.MalformedCode -> stringResource(R.string.qr_error_malformed)
                QrScanState.Error.Unreachable -> stringResource(R.string.qr_error_unreachable)
                is QrScanState.Error.Failed -> state.error.error.message.asString()
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        !hasCameraPermission -> Text(
            modifier = modifier,
            text = stringResource(R.string.qr_camera_denied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> Text(
            modifier = modifier,
            text = stringResource(R.string.qr_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun QrScanScreenPreview() {
    FServerTheme {
        QrScanScreen(
            state = QrScanState(),
            hasCameraPermission = true,
            onIntent = {},
            onRequestCameraPermission = {},
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
            hasCameraPermission = true,
            onIntent = {},
            onRequestCameraPermission = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Camera denied", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun QrScanNoCameraPreview() {
    FServerTheme {
        QrScanScreen(
            state = QrScanState(),
            hasCameraPermission = false,
            onIntent = {},
            onRequestCameraPermission = {},
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
            hasCameraPermission = true,
            onIntent = {},
            onRequestCameraPermission = {},
            navigateToManualAddress = {},
            navigateUp = {},
        )
    }
}
