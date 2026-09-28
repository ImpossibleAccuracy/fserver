package com.fserver.app.presentation.screens.settings.storage.main.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun ClearEvictionPreviewsDialog(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(text = stringResource(R.string.storage_clear_previews_title)) },
        text = { Text(text = stringResource(R.string.storage_clear_previews_body)) },
        confirmButton = {
            DkGhostButton(
                text = stringResource(R.string.action_clear),
                danger = true,
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

@Preview
@Composable
private fun ClearEvictionPreviewsDialogPreview() {
    FServerTheme {
        ClearEvictionPreviewsDialog(onConfirm = {}, onDismiss = {})
    }
}
