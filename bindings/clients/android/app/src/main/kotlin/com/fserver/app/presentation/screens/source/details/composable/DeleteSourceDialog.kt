package com.fserver.app.presentation.screens.source.details.composable

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
fun DeleteSourceDialog(
    modifier: Modifier = Modifier,
    label: String,
    peerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(text = stringResource(R.string.source_delete_title, label)) },
        text = { Text(text = stringResource(R.string.source_delete_body, peerName)) },
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

@Preview
@Composable
private fun DeleteSourceDialogPreview() {
    FServerTheme {
        DeleteSourceDialog(label = "Documents", peerName = "Laptop", onConfirm = {}, onDismiss = {})
    }
}
