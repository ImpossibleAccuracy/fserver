package com.fserver.app.presentation.screens.source.setup.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkActionBar
import com.fserver.app.presentation.designkit.DkCheckState
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import com.fserver.app.presentation.designkit.DkPrimaryButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkStatusRow
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.source.setup.access.model.SourceAccessIntent
import com.fserver.app.presentation.screens.source.setup.access.model.SourceAccessState
import com.fserver.app.presentation.screens.source.setup.access.model.SourceAccessUiEffect
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceAccessUi
import com.fserver.app.presentation.screens.source.setup.shared.rememberSourceFileOpener
import com.fserver.app.presentation.screens.source.shared.composable.SourceAccessFailure
import com.fserver.app.presentation.screens.source.shared.composable.SourceScanResult
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreview
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreviewHeader
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreviewSelection
import com.fserver.app.presentation.screens.source.shared.preview.composable.layouts.displayLabel
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import com.fserver.core.files.scan.DirectoryScanProgress

@Composable
fun SourceAccessScreen(
    handler: SourceAccessHandler,
    navigateToMode: (SourceAccessUi) -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateUp: () -> Unit,
) {
    val fileOpener = rememberSourceFileOpener()
    val state = handler.state.collectAsStateWithLifecycle().value ?: return

    val requester = rememberSourceAccessRequester { grant ->
        handler.onIntent(SourceAccessIntent.AccessAnswered(grant))
    }

    LaunchedEffect(handler) {
        handler.effects.collect { effect ->
            when (effect) {
                SourceAccessUiEffect.NavigateToMode ->
                    navigateToMode(handler.state.value?.access ?: SourceAccessUi.Full)
            }
        }
    }

    SourceAccessScreenContent(
        state = state,
        newIntent = handler::onIntent,
        onRequestAccess = { requester.request(state.kind) },
        onFileClick = { fileOpener.open(it) },
        onContinue = {
            if (state.isPickingDirectory) {
                handler.onIntent(SourceAccessIntent.DirectoryConfirmed)
                return@SourceAccessScreenContent
            }

            handler.onIntent(SourceAccessIntent.Confirmed)
            navigateToMode(state.access)
        },
        navigateToSourcePick = navigateToSourcePick,
        navigateUp = navigateUp,
    )
}

@Composable
private fun SourceAccessScreenContent(
    state: SourceAccessState,
    newIntent: (SourceAccessIntent) -> Unit,
    onRequestAccess: () -> Unit,
    onFileClick: (SourcePreviewUi.File) -> Unit,
    onContinue: () -> Unit,
    navigateToSourcePick: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val selected = state.selection?.selected

            DkTopBar(
                title = when (selected) {
                    null -> stringResource(state.kind.titleRes)
                    is SourcePreviewUi.Directory -> selected.displayLabel()
                    else -> selected.name
                },
                subtitle = when (selected) {
                    is SourcePreviewUi.Directory -> stringResource(
                        R.string.source_preview_directory_count,
                        selected.files,
                        selected.size.formatted(),
                    )

                    is SourcePreviewUi.File -> selected.size?.formatted()

                    else -> null
                },
                onBack = {
                    if (state.phase == SourceAccessState.Phase.Scanned &&
                        state.selection?.selected != null &&
                        state.selection.walkUp != null
                    ) {
                        state.selection.walkUp.invoke()
                    } else {
                        navigateUp()
                    }
                },
                actions = {
                    if (state.selection != null) {
                        DkIconButton(
                            icon = Icons.Default.Check,
                            onClick = onContinue,
                            enabled = state.selection.selected != null,
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (state.selection != null) return@DkScaffold

            DkActionBar {
                when (state.phase) {
                    SourceAccessState.Phase.Scanning -> DkGhostButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_cancel),
                        onClick = { newIntent(SourceAccessIntent.ScanCancelled) },
                    )

                    SourceAccessState.Phase.Scanned -> {
                        DkPrimaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.action_continue),
                            onClick = onContinue,
                        )
                        DkGhostButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.action_back),
                            onClick = navigateUp,
                        )
                    }

                    SourceAccessState.Phase.Denied -> {
                        DkPrimaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(state.kind.retryRes),
                            onClick = onRequestAccess,
                        )
                        DkGhostButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.source_error_back_to_sources),
                            onClick = navigateToSourcePick,
                        )
                    }

                    SourceAccessState.Phase.Explaining -> {
                        DkPrimaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(state.kind.continueRes),
                            onClick = onRequestAccess,
                        )
                        DkGhostButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.action_back),
                            onClick = navigateUp,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val bodyModifier = Modifier.padding(innerPadding)

        when (state.phase) {
            SourceAccessState.Phase.Scanning -> SourceScanResult(
                modifier = bodyModifier,
                title = stringResource(R.string.source_scan_folder_title),
                body = state.label.ifEmpty { stringResource(state.kind.titleRes) },
                detail = state.progress?.let { progress ->
                    stringResource(
                        R.string.source_scan_folder_summary,
                        progress.scannedFiles,
                        progress.scannedSize.formatted(),
                    )
                },
            )

            SourceAccessState.Phase.Scanned -> ScannedBody(
                modifier = bodyModifier,
                state = state,
                selection = state.selection,
                onFileClick = onFileClick,
            )

            SourceAccessState.Phase.Denied -> SourceAccessFailure(
                modifier = bodyModifier,
                title = stringResource(state.kind.errorTitleRes),
                body = stringResource(state.kind.errorBodyRes),
            )

            SourceAccessState.Phase.Explaining -> AccessExplainer(
                modifier = bodyModifier,
                kind = state.kind,
            )
        }
    }
}

/**
 * What the walk turned up: the files themselves, or — for the whole device — the folders it
 * passed through, since everything is not something a source may be pointed at.
 */
@Composable
private fun ScannedBody(
    modifier: Modifier = Modifier,
    state: SourceAccessState,
    selection: SourcePreviewSelection?,
    onFileClick: (SourcePreviewUi.File) -> Unit,
) {
    val preview = state.preview
    if (preview == null) {
        SourceScanResult(
            modifier = modifier,
            title = stringResource(R.string.source_scan_done_title),
            body = state.label.ifEmpty { stringResource(state.kind.titleRes) },
            detail = stringResource(
                R.string.source_scan_folder_summary,
                state.files,
                state.bytes.formatted(),
            ),
        )
        return
    }

    val summary = stringResource(
        R.string.source_scan_folder_summary,
        state.files,
        state.bytes.formatted(),
    )

    SourcePreview(
        modifier = modifier,
        preview = preview,
        onFileClick = onFileClick,
        selection = selection,
        header = {
            if (!state.isPickingDirectory) {
                SourcePreviewHeader(
                    title = state.label.ifEmpty { stringResource(state.kind.titleRes) },
                    detail = summary,
                )
            }
        },
    )
}

@Composable
private fun AccessExplainer(
    modifier: Modifier = Modifier,
    kind: SourceKindUi,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DkSpacing.screenPadding)
            .padding(bottom = DkSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.lg),
    ) {
        kind.illustrationRes?.let { illustration ->
            DkPlaceholderBox(
                label = stringResource(illustration),
                modifier = Modifier.height(160.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
            Text(
                text = stringResource(kind.headingRes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(kind.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (kind == SourceKindUi.Media) {
            Column {
                DkStatusRow(
                    title = stringResource(R.string.source_access_photos_visible),
                    detail = stringResource(R.string.source_access_photos_visible_detail),
                    state = DkCheckState.Ok,
                )
                DkStatusRow(
                    title = stringResource(R.string.source_access_photos_hidden),
                    detail = stringResource(R.string.source_access_photos_hidden_detail),
                    state = DkCheckState.Failed,
                )
            }
        }

        kind.noteRes?.let { note ->
            DkInfoBox(text = stringResource(note))
        }
    }
}

private val SourceKindUi.illustrationRes: Int?
    get() = when (this) {
        SourceKindUi.Media -> R.string.source_access_photos_illustration
        SourceKindUi.Folder -> R.string.source_access_folder_illustration
        // The dangerous branch gets no picture: nothing here should read as an invitation.
        SourceKindUi.WholeDevice -> null
        SourceKindUi.AppStorage -> null
    }

private val SourceKindUi.headingRes: Int
    get() = when (this) {
        SourceKindUi.Media -> R.string.source_access_photos_heading
        SourceKindUi.Folder -> R.string.source_access_folder_heading
        SourceKindUi.WholeDevice -> R.string.source_access_device_heading
        SourceKindUi.AppStorage -> R.string.source_access_appstorage_heading
    }

private val SourceKindUi.bodyRes: Int
    get() = when (this) {
        SourceKindUi.Media -> R.string.source_access_photos_body
        SourceKindUi.Folder -> R.string.source_access_folder_body
        SourceKindUi.WholeDevice -> R.string.source_access_device_body
        SourceKindUi.AppStorage -> R.string.source_access_appstorage_body
    }

private val SourceKindUi.noteRes: Int?
    get() = when (this) {
        SourceKindUi.Media -> null
        SourceKindUi.Folder -> R.string.source_access_folder_note
        SourceKindUi.WholeDevice -> R.string.source_access_device_note
        SourceKindUi.AppStorage -> R.string.source_access_appstorage_note
    }

private val SourceKindUi.continueRes: Int
    get() = when (this) {
        SourceKindUi.Media -> R.string.action_continue
        SourceKindUi.Folder -> R.string.source_access_folder_action
        SourceKindUi.WholeDevice -> R.string.source_access_device_action
        SourceKindUi.AppStorage -> R.string.source_access_appstorage_action
    }

private val SourceKindUi.errorTitleRes: Int
    get() = when (this) {
        SourceKindUi.Media -> R.string.source_error_photos_title
        SourceKindUi.Folder -> R.string.source_error_folder_title
        SourceKindUi.WholeDevice -> R.string.source_error_device_title
        SourceKindUi.AppStorage -> R.string.source_error_appstorage_title
    }

private val SourceKindUi.errorBodyRes: Int
    get() = when (this) {
        SourceKindUi.Media -> R.string.source_error_photos_body
        SourceKindUi.Folder -> R.string.source_error_folder_body
        SourceKindUi.WholeDevice -> R.string.source_error_device_body
        SourceKindUi.AppStorage -> R.string.source_error_appstorage_body
    }

private val SourceKindUi.retryRes: Int
    get() = when (this) {
        SourceKindUi.Media,
        SourceKindUi.Folder,
        SourceKindUi.AppStorage -> R.string.action_retry

        SourceKindUi.WholeDevice -> R.string.source_error_open_settings
    }

@Preview(showBackground = true)
@Composable
private fun SourceAccessPhotosPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(kind = SourceKindUi.Media),
            newIntent = {},
            onRequestAccess = {},
            onFileClick = {},
            onContinue = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Folder", showBackground = true)
@Composable
private fun SourceAccessFolderPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(kind = SourceKindUi.Folder),
            newIntent = {},
            onRequestAccess = {},
            onFileClick = {},
            onContinue = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Whole device", showBackground = true)
@Composable
private fun SourceAccessDevicePreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(kind = SourceKindUi.WholeDevice),
            newIntent = {},
            onRequestAccess = {},
            onFileClick = {},
            onContinue = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Denied", showBackground = true)
@Composable
private fun SourceAccessDeniedPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(
                kind = SourceKindUi.Media,
                phase = SourceAccessState.Phase.Denied,
            ),
            newIntent = {},
            onRequestAccess = {},
            onFileClick = {},
            onContinue = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Scanning", showBackground = true)
@Composable
private fun SourceAccessScanningPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(
                kind = SourceKindUi.Folder,
                phase = SourceAccessState.Phase.Scanning,
                label = "/DCIM/Projects",
                progress = DirectoryScanProgress(
                    scannedFiles = 412,
                    scannedSize = FileSize(3_100_000_000L),
                ),
            ),
            newIntent = {},
            onRequestAccess = {},
            onFileClick = {},
            onContinue = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Folder preview", showBackground = true)
@Composable
private fun SourceAccessPreviewPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(
                kind = SourceKindUi.Folder,
                phase = SourceAccessState.Phase.Scanned,
                label = "/DCIM/Projects",
                files = 842,
                bytes = FileSize(6_549_123_072L),
                preview = SourcePreviewUi.PlainList(
                    files = listOf(
                        SourcePreviewUi.File(
                            id = "1",
                            path = "IMG_0001.jpg",
                            name = "IMG_0001.jpg",
                            kind = FileKindUi.Image,
                            locator = "/storage/emulated/0/DCIM/Projects/IMG_0001.jpg",
                            size = FileSize(4_210_000),
                            extensionLabel = null,
                        ),
                        SourcePreviewUi.File(
                            id = "2",
                            path = "notes.pdf",
                            name = "notes.pdf",
                            kind = FileKindUi.Document,
                            locator = "/storage/emulated/0/DCIM/Projects/notes.pdf",
                            size = FileSize(820_000),
                            extensionLabel = "PDF",
                        ),
                    ),
                ),
            ),
            newIntent = {},
            onRequestAccess = {},
            onFileClick = {},
            onContinue = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "Directory pick", showBackground = true)
@Composable
private fun SourceAccessDirectoryPickPreview() {
    FServerTheme {
        SourceAccessScreenContent(
            state = SourceAccessState(
                kind = SourceKindUi.WholeDevice,
                phase = SourceAccessState.Phase.Scanned,
                files = 12_408,
                bytes = FileSize(41_200_000_000L),
                preview = SourcePreviewUi.Tree(
                    directories = listOf(
                        SourcePreviewUi.Directory(
                            path = "/storage/emulated/0",
                            name = "primary",
                            files = 12_408,
                            size = FileSize(41_200_000_000L),
                            isVolume = true,
                            contents = listOf(
                                SourcePreviewUi.Directory(
                                    path = "/storage/emulated/0/DCIM",
                                    name = "DCIM",
                                    files = 2_310,
                                    size = FileSize(19_100_000_000L),
                                    contents = listOf(
                                        SourcePreviewUi.Directory(
                                            path = "/storage/emulated/0/DCIM/Camera",
                                            name = "Camera",
                                            files = 2_140,
                                            size = FileSize(18_400_000_000L),
                                        ),
                                    ),
                                ),
                                SourcePreviewUi.Directory(
                                    path = "/storage/emulated/0/Download",
                                    name = "Download",
                                    files = 87,
                                    size = FileSize(1_240_000_000L),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            newIntent = {},
            onRequestAccess = {},
            onFileClick = {},
            onContinue = {},
            navigateToSourcePick = {},
            navigateUp = {},
        )
    }
}
