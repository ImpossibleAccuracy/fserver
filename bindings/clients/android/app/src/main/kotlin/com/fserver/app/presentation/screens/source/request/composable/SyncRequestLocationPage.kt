package com.fserver.app.presentation.screens.source.request.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.source.request.model.SyncRequestIntent
import com.fserver.app.presentation.screens.source.request.model.SyncRequestState
import com.fserver.app.presentation.screens.source.shared.composable.SourceChoiceRow
import com.fserver.app.presentation.screens.source.shared.model.HostLocationUi
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun SyncRequestLocationPage(
    modifier: Modifier = Modifier,
    state: SyncRequestState,
    onIntent: (SyncRequestIntent) -> Unit,
    onPickFolder: () -> Unit,
    onPickDirectory: () -> Unit,
) {
    val peerName = state.request?.deviceName.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DkSpacing.screenPadding)
            .padding(top = DkSpacing.lg, bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.sync_request_location_body, peerName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SourceChoiceRow(
            title = stringResource(R.string.sync_request_location_internal_title),
            description = stringResource(R.string.sync_request_location_internal_subtitle),
            selected = state.location == HostLocationUi.AppStorage,
            recommended = true,
            onSelect = { onIntent(SyncRequestIntent.AppStorageSelected) },
        )

        SourceChoiceRow(
            title = stringResource(R.string.sync_request_location_folder_title),
            description = stringResource(R.string.sync_request_location_folder_subtitle),
            selected = state.isFolderSelected,
            navigates = state.folder == null,
            onSelect = {
                if (state.folder == null) {
                    onPickFolder()
                } else {
                    onIntent(SyncRequestIntent.FolderSelected)
                }
            },
        ) {
            if (state.folder != null) {
                PickedFolder(label = state.folder.label, onChange = onPickFolder)
            }
        }

        SourceChoiceRow(
            title = stringResource(R.string.sync_request_location_device_title),
            description = stringResource(R.string.sync_request_location_device_subtitle),
            selected = state.isDirectorySelected,
            navigates = state.directory == null,
            onSelect = {
                if (state.directory == null) {
                    onPickDirectory()
                } else {
                    onIntent(SyncRequestIntent.DirectorySelected)
                }
            },
        ) {
            if (state.directory != null) {
                PickedFolder(label = state.directory.label, onChange = onPickDirectory)
            }
        }

        if (state.location.hasFiles) {
            DkInfoBox(text = stringResource(R.string.sync_request_location_folder_not_empty, peerName))
        }
    }
}

@Composable
private fun PickedFolder(
    modifier: Modifier = Modifier,
    label: String,
    onChange: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        DkIcon(icon = Icons.Default.Folder)
        DkMonoCaption(modifier = Modifier.weight(1f), text = label)
        DkGhostButton(
            text = stringResource(R.string.sync_request_location_folder_change),
            onClick = onChange,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SyncRequestLocationPagePreview() {
    val folder = HostLocationUi.Folder(uri = "content://tree", label = "/Documents/Projects", hasFiles = true)

    FServerTheme {
        SyncRequestLocationPage(
            state = SyncRequestState.Sample.copy(
                location = folder,
                folder = folder,
            ),
            onIntent = {},
            onPickFolder = {},
            onPickDirectory = {},
        )
    }
}
