package com.fserver.app.presentation.screens.source.details.composable

import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.shared.journal.model.icon
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkProgressBar
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.source.details.model.SourceDetailsState.AttentionUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import kotlin.math.roundToInt

/**
 * Something that stops the source until the user acts. Drawn in the alert colour with its one
 * way out, so it reads as a task and not as status.
 */
@Composable
fun AttentionCard(
    modifier: Modifier = Modifier,
    attention: AttentionUi,
    peerName: String,
    onResolveConflicts: () -> Unit,
    onDismissIssue: (entryId: Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.error, MaterialTheme.shapes.medium)
            .padding(DkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        when (attention) {
            is AttentionUi.Conflicts -> {
                Title(
                    icon = Icons.AutoMirrored.Filled.CallSplit,
                    text = stringResource(R.string.source_details_conflicts_title, attention.count),
                )
                Body(text = stringResource(R.string.source_details_conflicts_body))
                if (attention.fileNames.isNotEmpty()) {
                    DkCaption(text = attention.fileNames.joinToString(", "))
                }
                DkSecondaryButton(
                    text = stringResource(R.string.source_details_conflicts_resolve),
                    onClick = onResolveConflicts,
                )
            }

            is AttentionUi.LostOnPeer -> {
                Title(
                    icon = Icons.Default.ErrorOutline,
                    text = pluralStringResource(
                        R.plurals.source_details_lost_title,
                        attention.count,
                        attention.count,
                        peerName,
                    ),
                )
                Body(text = stringResource(R.string.source_details_lost_body, peerName))
                if (attention.fileNames.isNotEmpty()) {
                    DkCaption(text = attention.fileNames.joinToString(", "))
                }
            }

            is AttentionUi.Issue -> {
                Title(icon = attention.entry.kind.icon, text = attention.entry.title.asString())
                if (attention.entry.details.isNotEmpty()) {
                    Body(text = attention.entry.details.map { it.asString() }.joinToString(" · "))
                }
                DkGhostButton(
                    text = stringResource(R.string.action_dismiss),
                    onClick = { onDismissIssue(attention.entry.id) },
                )
            }

            is AttentionUi.PeerAlmostFull -> {
                Title(
                    icon = Icons.Default.WarningAmber,
                    text = stringResource(R.string.source_details_peer_full_title, peerName),
                )
                DkProgressBar(progress = attention.usedFraction)
                Body(
                    text = stringResource(
                        R.string.source_details_peer_full_used,
                        attention.usedPercent.roundToInt(),
                        peerName,
                        pluralStringResource(R.plurals.storage_files, attention.files, attention.files),
                        FileSize(attention.bytes).formatted(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun Title(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Body(modifier: Modifier = Modifier, text: String) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AttentionCardPreview() {
    FServerTheme {
        Column(
            modifier = Modifier.padding(DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            AttentionCard(
                attention = AttentionUi.Conflicts(2, listOf("Lease.docx", "Budget 2026.xlsx")),
                peerName = "Laptop",
                onResolveConflicts = {},
                onDismissIssue = {},
            )
            AttentionCard(
                attention = AttentionUi.LostOnPeer(1, listOf("IMG_0412.jpg")),
                peerName = "Server",
                onResolveConflicts = {},
                onDismissIssue = {},
            )
            AttentionCard(
                attention = AttentionUi.PeerAlmostFull(usedPercent = 92f, files = 8940, bytes = 36_000_000_000),
                peerName = "Server",
                onResolveConflicts = {},
                onDismissIssue = {},
            )
        }
    }
}
