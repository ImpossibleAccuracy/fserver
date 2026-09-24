package com.fserver.app.presentation.screens.settings.mydevice.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.localizedName
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkFingerprintBlock
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkQrCode
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.screens.settings.mydevice.model.MyDeviceState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.TransportKind

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
    invitation: MyDeviceState.InvitationUi.Ready,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        DkQrCode(payload = invitation.payload)

        DkCaption(text = stringResource(R.string.devices_qr_hint))

        if (invitation.addresses.isNotEmpty()) {
            DkSectionLabel(text = stringResource(R.string.devices_qr_address_label))

            invitation.addresses.forEach { AddressRow(address = it) }
        }

        DkSectionLabel(text = stringResource(R.string.devices_qr_fingerprint_label))

        DkFingerprintBlock(groups = invitation.fingerprintGroups)
    }
}

/** One address the device answers on, with the method that owns it named next to it. */
@Composable
private fun AddressRow(
    modifier: Modifier = Modifier,
    address: MyDeviceState.AddressUi,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = DkSpacing.md, vertical = DkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DkMonoCaption(modifier = Modifier.weight(1f), text = address.address)

        address.transport?.let {
            DkTag(text = stringResource(it.localizedName), style = DkTagStyle.Outline)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionQrSheetPreview() {
    FServerTheme {
        ConnectionQrSheet(
            invitation = MyDeviceState.InvitationUi.Ready(
                payload = """{"ip":"192.168.1.42","port":29470,"deviceId":"a1","nearby":true}""",
                addresses = listOf(
                    MyDeviceState.AddressUi("192.168.1.42:29470", TransportKind.MulticastDns),
                ),
                fingerprintGroups = listOf("9f2c", "4a01", "b7d3", "e820"),
            ),
            onDismiss = {},
        )
    }
}
