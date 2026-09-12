package com.fserver.app.presentation.screens.files.list.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.composable.model.labelRes
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.screens.files.list.model.FilesState
import com.fserver.app.presentation.screens.source.shared.preview.composable.icon
import com.fserver.app.presentation.theme.FServerTheme

/**
 * One line of the feed, whatever it holds and wherever it lives.
 *
 * The trailing marker is the only thing separating a local file from an offloaded one or from a
 * file on someone else's device — one list, three origins, no third screen.
 */
@Composable
fun ContentEntryRow(
    modifier: Modifier = Modifier,
    entry: FilesState.EntryUi,
    onClick: () -> Unit,
) {
    val availabilityIcon = entry.file.availability.icon

    DkListRow(
        modifier = modifier,
        title = entry.file.name,
        subtitle = if (entry.conflicted) {
            stringResource(R.string.files_state_conflict)
        } else {
            entry.subtitle()
        },
        subtitleColor = MaterialTheme.colorScheme.error.takeIf { entry.conflicted },
        onClick = onClick,
        leading = {
            DkThumbnail(
                icon = if (entry.isFolder) Icons.Default.Folder else entry.file.kind.icon()
            )
        },
        trailing = {
            when {
                entry.isFolder -> DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)

                availabilityIcon != null -> DkIcon(
                    icon = availabilityIcon,
                    contentDescription = stringResource(entry.file.availability.labelRes),
                )
            }
        },
    )
}

@Composable
private fun FilesState.EntryUi.subtitle(): String? = when {
    isFolder -> childLabel
    else -> listOfNotNull(file.sizeLabel, file.dateLabel)
        .joinToString(" · ")
        .takeIf { it.isNotEmpty() }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ContentEntryRowPreview() {
    FServerTheme {
        Column {
            FilesState.SampleEntries.forEach { entry ->
                ContentEntryRow(entry = entry, onClick = {})
            }
        }
    }
}
