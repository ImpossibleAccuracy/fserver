package com.fserver.app.presentation.screens.settings.storage.source.composable

import androidx.compose.foundation.background
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
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

/** What a selection adds up to, and the one thing to do with it. */
@Composable
fun SelectionBar(
    modifier: Modifier = Modifier,
    count: Int,
    bytes: Long,
    onDelete: () -> Unit,
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
            text = stringResource(R.string.action_delete),
            enabled = count > 0,
            onClick = onDelete,
        )
    }
}

/** Asked only when some of the files have no copy anywhere else yet. */
@Composable
fun DeleteUncopiedDialog(
    modifier: Modifier = Modifier,
    count: Int,
    uncopied: Int,
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(text = pluralStringResource(R.plurals.storage_delete_title, count, count)) },
        text = {
            Text(
                text = pluralStringResource(
                    R.plurals.storage_delete_uncopied,
                    uncopied,
                    uncopied,
                    deviceName,
                ),
            )
        },
        confirmButton = {
            DkGhostButton(
                text = stringResource(R.string.action_delete),
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            )
        },
        dismissButton = {
            DkGhostButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SelectionBarPreview() {
    FServerTheme {
        SelectionBar(count = 3, bytes = 8_200_000_000, onDelete = {})
    }
}
