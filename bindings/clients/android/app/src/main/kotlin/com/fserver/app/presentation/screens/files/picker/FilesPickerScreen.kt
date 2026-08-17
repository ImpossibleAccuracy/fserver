package com.fserver.app.presentation.screens.files.picker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.files.picker.composable.DirectoryTreeView
import com.fserver.app.presentation.screens.files.picker.composable.MediaTabsView
import com.fserver.app.presentation.screens.files.picker.composable.PickedEntryList
import com.fserver.app.presentation.screens.files.picker.composable.PickerSourceList
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerIntent
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState.PickerSource
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun FilesPickerScreen(
    viewModel: FilesPickerViewModel = koinViewModel(),
    navigateToSendTarget: (selectionId: String) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is FilesPickerUiEffect.SelectionReady -> navigateToSendTarget(effect.selectionId)
            }
        }
    }

    val context = LocalContext.current
    val fileTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        // Save the permission to access this URI persistently
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)

        viewModel.onIntent(
            FilesPickerIntent.UriPicked(uri)
        )
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // A partial grant is still a grant: the scan returns whatever the user allowed.
        if (results.values.any { it }) {
            viewModel.onIntent(FilesPickerIntent.MediaAccessGranted)
        }
    }

    FilesPickerScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        onPickerSourceSelected = { source ->
            when (source) {
                PickerSource.StorageAccessFramework -> fileTreeLauncher.launch(null)
                PickerSource.MediaStore -> {
                    val missing = mediaPermissions().filterNot { permission ->
                        ContextCompat.checkSelfPermission(context, permission) ==
                                PackageManager.PERMISSION_GRANTED
                    }

                    if (missing.isEmpty()) {
                        viewModel.onIntent(FilesPickerIntent.MediaAccessGranted)
                    } else {
                        mediaPermissionLauncher.launch(missing.toTypedArray())
                    }
                }

                PickerSource.FullAccess -> {
                    @SuppressLint("NewApi")
                    if (Environment.isExternalStorageManager()) {
                        viewModel.onIntent(
                            FilesPickerIntent.FullAccessGranted
                        )
                    } else {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        val uri = Uri.fromParts("package", context.packageName, null)
                        intent.data = uri
                        context.startActivity(intent) // TODO: use result launcher instead of starting activity directly
                    }
                }
            }
        },
        onDone = { viewModel.onIntent(FilesPickerIntent.DoneClicked) },
        navigateUp = navigateUp,
    )
}

/**
 * Permissions covering the image, video and audio collections on this API level.
 *
 * From API 33 the single storage permission is split per media type; from API 34 the user may
 * answer with a hand-picked subset instead, which arrives as its own permission. Requesting the
 * subset permission alongside the others is what makes that answer possible — without it the
 * dialog offers only all-or-nothing.
 */
private fun mediaPermissions(): List<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
    )

    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * The selection the user is assembling to send. The screen is the source chooser until a
 * source has been opened, and that source's own browser afterwards — each one selects in the
 * shape its access model allows:
 *
 * - the system picker hands over what the user already chose, so those entries arrive selected;
 * - full access browses the device as a tree and selects whole directories;
 * - MediaStore has no directory structure worth walking, so it groups into tabs and selects
 *   individual items across them.
 *
 * Back leaves the open source and returns to the chooser; Done commits whatever the three
 * sources have contributed together.
 */
@Composable
private fun FilesPickerScreenContent(
    state: FilesPickerState,
    onIntent: (FilesPickerIntent) -> Unit,
    onPickerSourceSelected: (PickerSource) -> Unit,
    onDone: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        containerColor = Color.Transparent,
        topBar = {
            DkTopBar(
                title = stringResource(state.titleRes()),
                onBack = {
                    if (state.activeSource != null) {
                        onIntent(FilesPickerIntent.SourceClosed)
                    } else {
                        navigateUp()
                    }
                },
                actions = {
                    DkGhostButton(
                        text = stringResource(R.string.action_done),
                        onClick = onDone,
                        enabled = state.canConfirm,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(bottom = DkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            when (state.activeSource) {
                null -> SourceChooser(
                    state = state,
                    onPickerSourceSelected = onPickerSourceSelected,
                )

                PickerSource.StorageAccessFramework -> SafSelection(
                    state = state,
                    onIntent = onIntent,
                )

                PickerSource.FullAccess -> DirectoryTreeView(
                    nodes = state.tree,
                    onExpandToggle = {
                        onIntent(FilesPickerIntent.DirectoryExpansionToggled(it))
                    },
                    onSelectionToggle = {
                        onIntent(FilesPickerIntent.DirectorySelectionToggled(it))
                    },
                )

                PickerSource.MediaStore -> MediaTabsView(
                    grouping = state.mediaGrouping,
                    tabs = state.mediaTabs,
                    activeTab = state.activeMediaTab,
                    onGroupingSelected = {
                        onIntent(FilesPickerIntent.MediaGroupingSelected(it))
                    },
                    onTabSelected = { onIntent(FilesPickerIntent.MediaTabSelected(it)) },
                    onItemToggle = { onIntent(FilesPickerIntent.MediaSelectionToggled(it)) },
                )
            }
        }
    }
}

@Composable
private fun SourceChooser(
    state: FilesPickerState,
    onPickerSourceSelected: (PickerSource) -> Unit,
) {
    PickerSourceList(
        sources = state.sources,
        onSourceSelected = onPickerSourceSelected,
    )

    if (state.canConfirm) {
        DkCaption(
            modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
            text = stringResource(R.string.picker_entries_title, state.selectedCount),
        )
    }
}

/**
 * The system picker has already done the choosing, so this view only shows what came back —
 * every entry counts as selected, and a swipe is how one is dropped again.
 */
@Composable
private fun SafSelection(
    state: FilesPickerState,
    onIntent: (FilesPickerIntent) -> Unit,
) {
    if (state.isEmpty) return

    DkCaption(
        modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
        text = stringResource(R.string.picker_entries_title, state.entries.size),
    )

    PickedEntryList(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        entries = state.entries,
        onRemove = { onIntent(FilesPickerIntent.EntryRemoved(it)) },
    )
}

@StringRes
private fun FilesPickerState.titleRes(): Int = when (activeSource) {
    null -> R.string.picker_title
    PickerSource.StorageAccessFramework -> R.string.picker_source_saf_title
    PickerSource.MediaStore -> R.string.picker_source_media_title
    PickerSource.FullAccess -> R.string.picker_source_full_title
}

@Preview(showBackground = true)
@Composable
private fun FilesPickerScreenPreview() {
    FServerTheme {
        FilesPickerScreenContent(
            state = FilesPickerState(
                activeSource = PickerSource.StorageAccessFramework,
                entries = FilesPickerState.SampleEntries,
                selectedCount = FilesPickerState.SampleEntries.size,
            ),
            onIntent = {},
            onPickerSourceSelected = {},
            onDone = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FilesPickerTreePreview() {
    FServerTheme {
        FilesPickerScreenContent(
            state = FilesPickerState(
                activeSource = PickerSource.FullAccess,
                tree = FilesPickerState.SampleTree,
                selectedCount = 1,
            ),
            onIntent = {},
            onPickerSourceSelected = {},
            onDone = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FilesPickerMediaPreview() {
    FServerTheme {
        FilesPickerScreenContent(
            state = FilesPickerState(
                activeSource = PickerSource.MediaStore,
                mediaTabs = FilesPickerState.SampleMediaTabs,
                activeMediaTabId = FilesPickerState.SampleMediaTabs.first().id,
                selectedCount = 1,
            ),
            onIntent = {},
            onPickerSourceSelected = {},
            onDone = {},
            navigateUp = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FilesPickerScreenEmptyPreview() {
    FServerTheme {
        FilesPickerScreenContent(
            state = FilesPickerState(),
            onIntent = {},
            onPickerSourceSelected = {},
            onDone = {},
            navigateUp = {},
        )
    }
}
