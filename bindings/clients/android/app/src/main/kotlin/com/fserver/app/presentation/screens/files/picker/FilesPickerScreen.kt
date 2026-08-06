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
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.screens.files.picker.composable.PickedEntryList
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerIntent
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun FilesPickerScreen(
    viewModel: FilesPickerViewModel = koinViewModel(),
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                FilesPickerState.PickerSource.StorageAccessFramework -> fileTreeLauncher.launch(null)
                FilesPickerState.PickerSource.MediaStore -> {
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
                FilesPickerState.PickerSource.FullAccess -> {
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
 * The selection the user is assembling to send: files and whole directories in one list.
 *
 * A row is dropped by swiping it away rather than by a trailing button — the delete target
 * is the row itself, and nothing here removes anything from the device.
 */
@Composable
private fun FilesPickerScreenContent(
    state: FilesPickerState,
    onIntent: (FilesPickerIntent) -> Unit,
    onPickerSourceSelected: (FilesPickerState.PickerSource) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        containerColor = Color.Transparent,
        topBar = {
            DkTopBar(
                title = stringResource(R.string.picker_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(bottom = DkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            Column {
                state.sources.forEach { source ->
                    DkListRow(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onPickerSourceSelected(source) },
                        title = when (source) {
                            FilesPickerState.PickerSource.StorageAccessFramework -> "Select from Files"
                            FilesPickerState.PickerSource.MediaStore -> "Select from Media"
                            FilesPickerState.PickerSource.FullAccess -> "Select from Full Access"
                        },
                        subtitle = when (source) {
                            FilesPickerState.PickerSource.StorageAccessFramework -> "Browse files using the system file picker"
                            FilesPickerState.PickerSource.MediaStore -> "Select from your photos, videos, and audio"
                            FilesPickerState.PickerSource.FullAccess -> "Browse all files on your device"
                        },
                        subtitleStyle = DkType.mono,
                        leading = {
                            DkThumbnail(
                                icon = when (source) {
                                    FilesPickerState.PickerSource.StorageAccessFramework -> Icons.Default.AttachFile
                                    FilesPickerState.PickerSource.MediaStore -> Icons.Default.Image
                                    FilesPickerState.PickerSource.FullAccess -> Icons.Default.FileCopy
                                },
                            )
                        },
                        trailing = {
                            DkIcon(icon = Icons.Default.ChevronRight)
                        },
                    )
                }
            }

            if (!state.isEmpty) {
                DkCaption(
                    modifier = Modifier.padding(horizontal = DkSpacing.screenPadding),
                    text = "Selected entries (${state.entries.size})"
                )

                PickedEntryList(
                    entries = state.entries,
                    onRemove = { onIntent(FilesPickerIntent.EntryRemoved(it)) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FilesPickerScreenPreview() {
    FServerTheme {
        FilesPickerScreenContent(
            state = FilesPickerState(entries = FilesPickerState.SampleEntries),
            onIntent = {},
            onPickerSourceSelected = {},
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
            navigateUp = {},
        )
    }
}
