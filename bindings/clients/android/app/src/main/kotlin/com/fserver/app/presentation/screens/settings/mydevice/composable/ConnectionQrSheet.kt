package com.fserver.app.presentation.screens.settings.mydevice.composable

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkQrCode
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.dkQrBitmap
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.settings.mydevice.model.MyDeviceState
import com.fserver.app.presentation.theme.FServerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionQrSheet(
    modifier: Modifier = Modifier,
    invitation: MyDeviceState.InvitationUi,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
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
                text = stringResource(R.string.devices_qr_title),
                style = MaterialTheme.typography.titleLarge,
            )

            when (invitation) {
                MyDeviceState.InvitationUi.Loading -> PendingCode()

                MyDeviceState.InvitationUi.Unavailable -> DkCaption(
                    text = stringResource(R.string.devices_qr_unavailable),
                )

                is MyDeviceState.InvitationUi.Ready -> ReadyCode(invitation = invitation)
            }
        }
    }
}

/**
 * Holds the height the code will take, so the sheet does not jump the moment it arrives - the wait
 * is short, and a sheet that resizes under the user's thumb reads as a glitch.
 */
@Composable
private fun PendingCode(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DkInlineSpinner()
            DkCaption(text = stringResource(R.string.devices_qr_loading))
        }
    }
}

@Composable
private fun ReadyCode(
    modifier: Modifier = Modifier,
    invitation: MyDeviceState.InvitationUi.Ready,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.devices_qr_share_chooser)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        DkQrCode(payload = invitation.payload)

        DkCaption(text = stringResource(R.string.devices_qr_hint))

        DkSecondaryButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.devices_qr_share),
            icon = Icons.Default.Share,
            onClick = {
                scope.launch { context.shareQrCode(invitation.payload, chooserTitle) }
            },
        )
    }
}

private suspend fun Context.shareQrCode(payload: String, chooserTitle: String) {
    val uri = withContext(Dispatchers.IO) {
        val bitmap = dkQrBitmap(payload) ?: return@withContext null
        val file = File(cacheDir, "shared/connection-code.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(this@shareQrCode, "$packageName.fileprovider", file)
    } ?: return

    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    startActivity(Intent.createChooser(send, chooserTitle))
}

@Preview(showBackground = true)
@Composable
private fun ConnectionQrSheetPreview() {
    FServerTheme {
        ConnectionQrSheet(
            invitation = MyDeviceState.InvitationUi.Ready(
                payload = """{"ip":"192.168.1.42","port":29470,"deviceId":"a1","nearby":true}""",
                addresses = MyDeviceState.SampleInvitation.addresses,
                fingerprintGroups = MyDeviceState.SampleInvitation.fingerprintGroups,
            ),
            onDismiss = {},
        )
    }
}
