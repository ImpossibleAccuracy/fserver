package com.fserver.app.presentation.screens.source.request.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.request.location.model.SyncRequestLocationIntent
import com.fserver.app.presentation.screens.source.request.location.model.SyncRequestLocationState
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessFailure
import com.fserver.app.presentation.screens.source.shared.composable.SourceChoiceRow
import com.fserver.app.presentation.screens.source.shared.model.HostLocationUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SyncRequestLocationScreen(
    modifier: Modifier = Modifier,
    key: Destination.Source.Request.Location,
    viewModel: SyncRequestLocationViewModel = koinViewModel { parametersOf(key) },
    navigateToPreferences: (HostLocationUi) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(context::persistHostTree)?.fold(
            onSuccess = viewModel::onIntent,
            // A folder the user picked and the app then cannot write to is not a no-op: without
            // this the pick silently does nothing.
            onFailure = {
                viewModel.reporter.report(
                    error = it,
                    context = "Could not take a write grant on %s".format(uri)
                )
            },
        )
    }

    SyncRequestLocationContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        onPickFolder = { folderLauncher.launch(null) },
        navigateToPreferences = navigateToPreferences,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SyncRequestLocationContent(
    modifier: Modifier = Modifier,
    state: SyncRequestLocationState,
    onIntent: (SyncRequestLocationIntent) -> Unit,
    onPickFolder: () -> Unit,
    navigateToPreferences: (HostLocationUi) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.sync_request_location_title),
                subtitle = state.request?.label,
                onBack = navigateUp,
            )
        },
        bottomBar = {
            DkActionBar {
                if (state.isGone) {
                    DkPrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_back),
                        onClick = navigateUp,
                    )
                    return@DkActionBar
                }

                DkPrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_next),
                    onClick = { navigateToPreferences(state.selected) },
                    enabled = state.canContinue,
                )
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_back),
                    onClick = navigateUp,
                )
            }
        },
    ) { innerPadding ->
        if (state.isGone) {
            SourceAccessFailure(
                modifier = Modifier.padding(innerPadding),
                title = stringResource(R.string.sync_request_gone_title),
                body = stringResource(R.string.sync_request_gone_body),
            )
            return@DkScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DkSpacing.screenPadding)
                .padding(bottom = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.sync_request_location_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SourceChoiceRow(
                title = stringResource(R.string.sync_request_location_internal_title),
                description = stringResource(R.string.sync_request_location_internal_subtitle),
                selected = !state.isFolderSelected,
                recommended = true,
                onSelect = { onIntent(SyncRequestLocationIntent.AppStorageSelected) },
            )

            SourceChoiceRow(
                title = stringResource(R.string.sync_request_location_folder_title),
                description = state.folder?.label
                    ?: stringResource(R.string.sync_request_location_folder_subtitle),
                selected = state.isFolderSelected,
                onSelect = onPickFolder,
            )
        }
    }
}

private fun Context.persistHostTree(uri: Uri): Result<SyncRequestLocationIntent.FolderPicked> {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    return runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
        .map {
            val label = runCatching { DocumentsContract.getTreeDocumentId(uri) }
                .getOrNull()
                ?.substringAfter(':')
                ?.takeIf { segment -> segment.isNotEmpty() }
                ?: uri.lastPathSegment.orEmpty()

            SyncRequestLocationIntent.FolderPicked(uri = uri.toString(), label = "/$label")
        }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestLocationPreview() {
    FServerTheme {
        SyncRequestLocationContent(
            state = SyncRequestLocationState(
                request = SyncRequestUi(
                    sourceId = "3f2a",
                    deviceName = "MacBook-Pro",
                    label = "DCIM/Projects",
                    mode = SourceModeUi.Sync,
                ),
                isLoaded = true,
            ),
            onIntent = {},
            onPickFolder = {},
            navigateToPreferences = {},
            navigateUp = {},
        )
    }
}
