package com.fserver.app.presentation.screens.files.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.theme.FServerTheme
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

/**
 * What the "+" button asks now.
 *
 * It used to open the picker straight away, which answered a question the user had not been
 * asked: sending a file needs somewhere to send it. The fork comes first, and picking files is
 * one screen further in.
 */
@Composable
fun FilesActionsSheet(
    navigateToConnect: () -> Unit,
    navigateToSourcePick: () -> Unit,
    dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = DkSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        Text(
            modifier = Modifier.padding(
                horizontal = DkSpacing.screenPadding,
                vertical = DkSpacing.sm,
            ),
            text = stringResource(R.string.files_actions),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        DkListRow(
            title = stringResource(R.string.fork_connect_title),
            subtitle = stringResource(R.string.fork_connect_subtitle),
            subtitleMaxLines = 2,
            onClick = navigateToConnect,
            leading = { DkThumbnail(icon = Icons.Default.SwapHoriz) },
            trailing = { DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight) },
        )
        DkListRow(
            title = stringResource(R.string.fork_send_title),
            subtitle = stringResource(R.string.fork_send_sheet_subtitle),
            subtitleMaxLines = 2,
            onClick = navigateToSourcePick,
            leading = { DkThumbnail(icon = Icons.Default.Upload) },
            trailing = { DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight) },
        )

        DkGhostButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = DkSpacing.screenPadding,
                    vertical = DkSpacing.sm,
                ),
            text = stringResource(R.string.action_cancel),
            onClick = dismiss,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun FilesActionsSheetPreview() {
    FServerTheme {
        FilesActionsSheet(
            navigateToConnect = {},
            navigateToSourcePick = {},
            dismiss = {},
        )
    }
}
