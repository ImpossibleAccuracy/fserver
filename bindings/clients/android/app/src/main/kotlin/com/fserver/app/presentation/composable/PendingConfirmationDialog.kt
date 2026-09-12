package com.fserver.app.presentation.composable

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import com.fserver.app.presentation.composable.model.labelRes
import com.fserver.app.presentation.designkit.DkFingerprintBlock
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.core.network.device.model.PendingConfirmation


data class PendingConfirmationUi(
    val deviceId: String,
    val codeGroups: List<String>,
    val fingerprintGroups: List<String>,
    val reason: Reason,
) {
    sealed interface Reason {
        data object FirstContact : Reason

        data class Downgrade(@param:StringRes val pinnedMethodLabel: Int?) : Reason

        data class KeyChanged(val knownFingerprints: List<List<String>>) : Reason
    }

    /** Whether something on file contradicts this peer, which is what decides the whole sheet. */
    val isContradicted: Boolean get() = reason !is Reason.FirstContact
}

fun PendingConfirmation.toUi(): PendingConfirmationUi = PendingConfirmationUi(
    deviceId = deviceId,
    codeGroups = codeGroups,
    fingerprintGroups = fingerprintGroups,
    reason = reason.toUi(),
)

private fun PendingConfirmation.Reason.toUi(): PendingConfirmationUi.Reason = when (this) {
    PendingConfirmation.Reason.FirstContact -> PendingConfirmationUi.Reason.FirstContact

    is PendingConfirmation.Reason.Downgrade ->
        PendingConfirmationUi.Reason.Downgrade(pinnedMethod?.labelRes)

    is PendingConfirmation.Reason.KeyChanged ->
        PendingConfirmationUi.Reason.KeyChanged(knownFingerprints)
}


/**
 * The one place a peer is accepted or refused.
 *
 * A key that changed under a known device id is drawn as a warning and leads with the refusal: the
 * code below it is derived from the connection being questioned, so comparing it proves only that
 * both ends of *this* link agree, and the device it should be compared against may not be the one
 * on the other end.
 */
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
                text = stringResource(request.titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = when {
                    request.isContradicted -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )

            WarningBlock(reason = request.reason, deviceId = request.deviceId)

            Text(
                text = stringResource(R.string.peer_confirm_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (request.codeGroups.isNotEmpty()) {
                DkFingerprintBlock(groups = request.codeGroups)
            }

            DkSectionLabel(text = stringResource(R.string.peer_confirm_offered_key))
            DkFingerprintBlock(groups = request.fingerprintGroups)

            (request.reason as? PendingConfirmationUi.Reason.KeyChanged)?.let { changed ->
                DkSectionLabel(text = stringResource(R.string.peer_confirm_known_key))
                changed.knownFingerprints.forEach { DkFingerprintBlock(groups = it) }
            }

            Text(
                text = stringResource(R.string.peer_confirm_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm, Alignment.End),
            ) {
                // A contradicted peer leads with the refusal: accepting one is a deliberate act,
                // never the button a user reaches for without reading.
                if (request.isContradicted) {
                    DkGhostButton(
                        text = stringResource(R.string.peer_confirm_accept_anyway),
                        onClick = onConfirm,
                    )
                    DkPrimaryButton(
                        text = stringResource(R.string.peer_confirm_reject),
                        onClick = onReject,
                    )
                } else {
                    DkGhostButton(
                        text = stringResource(R.string.peer_confirm_reject),
                        onClick = onReject,
                    )
                    DkPrimaryButton(
                        text = stringResource(R.string.peer_confirm_accept),
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@get:StringRes
private val PendingConfirmationUi.titleRes: Int
    get() = when (reason) {
        PendingConfirmationUi.Reason.FirstContact -> R.string.peer_confirm_title
        is PendingConfirmationUi.Reason.Downgrade -> R.string.peer_confirm_title_downgrade
        is PendingConfirmationUi.Reason.KeyChanged -> R.string.peer_confirm_title_key_changed
    }

@Composable
private fun WarningBlock(
    reason: PendingConfirmationUi.Reason,
    deviceId: String,
    modifier: Modifier = Modifier,
) {
    val text = when (reason) {
        PendingConfirmationUi.Reason.FirstContact -> null

        is PendingConfirmationUi.Reason.KeyChanged ->
            stringResource(R.string.peer_confirm_warning_key_changed, deviceId)

        is PendingConfirmationUi.Reason.Downgrade -> when (val method = reason.pinnedMethodLabel) {
            null -> stringResource(R.string.peer_confirm_warning_downgrade_unknown)
            else -> stringResource(
                R.string.peer_confirm_warning_downgrade,
                stringResource(method),
            )
        }
    } ?: return

    Text(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(DkSpacing.md),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Preview(showBackground = true)
@Composable
private fun PendingConfirmationDialogPreview() {
    FServerTheme {
        PendingConfirmationDialog(
            request = PendingConfirmationUi(
                deviceId = "alice-laptop",
                codeGroups = listOf("9f2c", "4a01", "b7d3", "e820"),
                fingerprintGroups = listOf("9f2c 4a01", "b7d3 e820"),
                reason = PendingConfirmationUi.Reason.FirstContact,
            ),
            onConfirm = {},
            onReject = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PendingConfirmationKeyChangedPreview() {
    FServerTheme {
        PendingConfirmationDialog(
            request = PendingConfirmationUi(
                deviceId = "alice-laptop",
                codeGroups = listOf("9f2c", "4a01", "b7d3", "e820"),
                fingerprintGroups = listOf("11ab 22cd", "33ef 4401"),
                reason = PendingConfirmationUi.Reason.KeyChanged(
                    knownFingerprints = listOf(listOf("9f2c 4a01", "b7d3 e820")),
                ),
            ),
            onConfirm = {},
            onReject = {},
        )
    }
}
