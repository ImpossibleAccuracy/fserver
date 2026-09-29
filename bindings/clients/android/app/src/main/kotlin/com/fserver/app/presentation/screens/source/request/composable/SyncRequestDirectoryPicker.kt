package com.fserver.app.presentation.screens.source.request.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.source.request.model.SyncRequestIntent
import com.fserver.app.presentation.screens.source.request.model.SyncRequestState.DirectoryPickerUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessFailure
import com.fserver.app.presentation.screens.source.shared.composable.SourceScanResult
import com.fserver.app.presentation.shared.browser.FileBrowser
import com.fserver.app.presentation.shared.browser.layouts.displayLabel
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import com.fserver.core.files.scan.DirectoryScanProgress

@Composable
fun SyncRequestDirectoryPicker(
    modifier: Modifier = Modifier,
    picker: DirectoryPickerUi,
    onIntent: (SyncRequestIntent) -> Unit,
    onRequestAccess: () -> Unit,
) {
    val opened = picker.navigation?.opened
    val back: () -> Unit = {
        if (opened != null) picker.navigation.onUp()
        else onIntent(SyncRequestIntent.DirectoryPickCancelled)
    }

    BackHandler(onBack = back)

    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = opened?.displayLabel()
                    ?: stringResource(R.string.sync_request_location_device_title),
                subtitle = opened?.let {
                    stringResource(R.string.file_browser_directory_count, it.files, it.size.formatted())
                },
                onBack = back,
                actions = {
                    if (picker.navigation != null) {
                        DkIconButton(
                            icon = Icons.Default.Check,
                            onClick = { onIntent(SyncRequestIntent.DirectoryConfirmed) },
                            enabled = picker.canConfirm,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (picker.phase == DirectoryPickerUi.Phase.Browsing) return@DkScaffold

            DkActionBar {
                when (picker.phase) {
                    DirectoryPickerUi.Phase.Denied -> DkPrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.source_error_open_settings),
                        onClick = onRequestAccess,
                    )

                    DirectoryPickerUi.Phase.Failed -> DkPrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_retry),
                        onClick = onRequestAccess,
                    )

                    DirectoryPickerUi.Phase.Scanning,
                    DirectoryPickerUi.Phase.Browsing -> Unit
                }
                DkGhostButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.action_cancel),
                    onClick = { onIntent(SyncRequestIntent.DirectoryPickCancelled) },
                )
            }
        },
    ) { innerPadding ->
        val bodyModifier = Modifier.padding(innerPadding)

        when (picker.phase) {
            DirectoryPickerUi.Phase.Scanning -> SourceScanResult(
                modifier = bodyModifier,
                title = stringResource(R.string.sync_request_location_device_scanning),
                body = stringResource(R.string.sync_request_location_device_title),
                detail = picker.progress?.let {
                    stringResource(
                        R.string.source_scan_folder_summary,
                        it.scannedFiles,
                        it.scannedSize.formatted(),
                    )
                },
            )

            DirectoryPickerUi.Phase.Browsing -> picker.preview?.let { preview ->
                FileBrowser(
                    modifier = bodyModifier,
                    preview = preview,
                    navigation = picker.navigation,
                    onFileClick = {},
                )
            }

            DirectoryPickerUi.Phase.Denied -> SourceAccessFailure(
                modifier = bodyModifier,
                title = stringResource(R.string.source_error_device_title),
                body = stringResource(R.string.source_error_device_body),
            )

            DirectoryPickerUi.Phase.Failed -> SourceAccessFailure(
                modifier = bodyModifier,
                title = stringResource(R.string.source_error_scan_title),
                body = stringResource(R.string.sync_request_location_device_failed),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestDirectoryPickerScanningPreview() {
    FServerTheme {
        SyncRequestDirectoryPicker(
            picker = DirectoryPickerUi(
                progress = DirectoryScanProgress(
                    scannedFiles = 4_120,
                    scannedSize = FileSize(9_300_000_000L),
                ),
            ),
            onIntent = {},
            onRequestAccess = {},
        )
    }
}

@Preview(name = "Denied", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SyncRequestDirectoryPickerDeniedPreview() {
    FServerTheme {
        SyncRequestDirectoryPicker(
            picker = DirectoryPickerUi(phase = DirectoryPickerUi.Phase.Denied),
            onIntent = {},
            onRequestAccess = {},
        )
    }
}
