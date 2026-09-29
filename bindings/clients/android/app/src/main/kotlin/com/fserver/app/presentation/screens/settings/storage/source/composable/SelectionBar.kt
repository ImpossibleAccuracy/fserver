package com.fserver.app.presentation.screens.settings.storage.source.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.RefusalUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

/** What a selection adds up to, and freeing it from this phone. */
@Composable
fun SelectionBar(
    modifier: Modifier = Modifier,
    count: Int,
    bytes: Long,
    onFree: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pluralStringResource(R.plurals.storage_files, count, count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DkMonoCaption(text = FileSize(bytes).formatted())
        }
        DkPrimaryButton(
            text = stringResource(R.string.storage_free_action),
            enabled = count > 0,
            onClick = onFree,
        )
    }
}

/** Asked only when some selected files must stay: says why, and frees the rest. */
@Composable
fun FreeRefusedDialog(
    modifier: Modifier = Modifier,
    freeable: Int,
    freeableBytes: Long,
    refusals: Map<RefusalUi, Int>,
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = if (freeable > 0) {
                    stringResource(R.string.storage_free_title, FileSize(freeableBytes).formatted())
                } else {
                    stringResource(R.string.storage_free_nothing_title)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.xs)) {
                RefusalUi.entries.forEach { refusal ->
                    val count = refusals[refusal] ?: return@forEach
                    Text(text = pluralStringResource(refusal.textRes, count, count, deviceName))
                }
                Text(text = stringResource(R.string.storage_free_refused_footer))
            }
        },
        confirmButton = {
            if (freeable > 0) {
                DkGhostButton(
                    text = stringResource(R.string.storage_free_action),
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                )
            }
        },
        dismissButton = {
            DkGhostButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
    )
}

private val RefusalUi.textRes: Int
    get() = when (this) {
        RefusalUi.NotOnPeer -> R.plurals.storage_free_refused_not_on_peer
        RefusalUi.PeerDiffers -> R.plurals.storage_free_refused_peer_differs
        RefusalUi.Unverified -> R.plurals.storage_free_refused_unverified
        RefusalUi.Pinned -> R.plurals.storage_free_refused_pinned
    }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SelectionBarPreview() {
    FServerTheme {
        SelectionBar(count = 3, bytes = 8_200_000_000, onFree = {})
    }
}
