package com.fserver.app.presentation.screens.files.list.composable

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.model.IncomingRequestUi
import com.fserver.app.presentation.theme.FServerTheme

/**
 * Incoming transfer, offered over whatever screen the user is on.
 *
 * Everything needed for the decision is on the sheet — who is sending, how much, exactly
 * which files, and where they will land. Android may refuse to raise this while the app
 * is backgrounded, so the same request is also delivered as a system notification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingFilesSheet(
    request: IncomingRequestUi,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                text = pluralStringResource(
                    R.plurals.incoming_title,
                    request.files.size,
                    request.files.size,
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            // The sender's name is the one thing in this line the user must actually read,
            // so it is lifted out of the muted body voice.
            val fromLine = stringResource(
                R.string.incoming_from,
                request.fromDeviceName,
                request.totalSizeLabel,
            )
            val emphasis = SpanStyle(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildAnnotatedString {
                    val deviceStart = fromLine.indexOf(request.fromDeviceName)
                    if (deviceStart < 0) {
                        append(fromLine)
                    } else {
                        append(fromLine.substring(0, deviceStart))
                        withStyle(emphasis) { append(request.fromDeviceName) }
                        append(fromLine.substring(deviceStart + request.fromDeviceName.length))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.xs)) {
                request.files.forEach { file ->
                    Text(
                        text = "${file.name} · ${file.sizeLabel}",
                        style = DkType.monoLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(R.string.incoming_save_to, request.destinationLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
private fun IncomingFilesSheetPreview() {
    FServerTheme {
        IncomingFilesSheet(
            request = SampleData.incomingRequest,
            onAccept = {},
            onDecline = {},
            onDismiss = {},
        )
    }
}
