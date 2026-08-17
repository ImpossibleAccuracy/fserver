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
import com.fserver.app.presentation.designkit.DkFingerprintBlock
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.device.model.PendingConfirmation


data class PendingConfirmationUi(
    val deviceId: String,
    val codeGroups: List<String>,
)

fun PendingConfirmation.toUi(): PendingConfirmationUi = PendingConfirmationUi(
    deviceId = deviceId,
    codeGroups = codeGroups,
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingConfirmationDialog(
    request: PendingConfirmationUi,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        // Dismissing decides nothing, so it declines: a link left hanging is worse than a no.
        onDismissRequest = onReject,
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
                text = stringResource(R.string.peer_confirm_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Text(
                text = stringResource(R.string.peer_confirm_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DkFingerprintBlock(groups = request.codeGroups)

            Text(
                text = stringResource(R.string.peer_confirm_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm, Alignment.End),
            ) {
                DkGhostButton(
                    text = stringResource(R.string.peer_confirm_reject),
                    onClick = onReject
                )
                DkPrimaryButton(
                    text = stringResource(R.string.peer_confirm_accept),
                    onClick = onConfirm
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun PendingConfirmationDialogPreview() {
    FServerTheme {
        PendingConfirmationDialog(
            request = PendingConfirmationUi(
                deviceId = "alice-laptop",
                codeGroups = listOf("9f2c", "4a01", "b7d3", "e820"),
            ),
            onConfirm = {},
            onReject = {},
        )
    }
}
