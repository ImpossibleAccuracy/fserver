package com.fserver.app.presentation.screens.files.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun DeleteFilesDialog(
    modifier: Modifier = Modifier,
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(text = pluralStringResource(R.plurals.files_delete_title, count, count)) },
        text = { Text(text = stringResource(R.string.files_delete_body)) },
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
private fun DeleteFilesDialogPreview() {
    FServerTheme {
        DeleteFilesDialog(count = 2, onConfirm = {}, onDismiss = {})
    }
}
