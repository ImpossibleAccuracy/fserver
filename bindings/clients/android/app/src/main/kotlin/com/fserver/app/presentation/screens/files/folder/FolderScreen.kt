package com.fserver.app.presentation.screens.files.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.designkit.DkInfoBox
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.files.folder.model.FolderIntent
import com.fserver.app.presentation.screens.files.folder.model.FolderState
import com.fserver.app.presentation.screens.files.folder.model.FolderUiEffect
import com.fserver.app.presentation.screens.files.list.model.FilesIntent
import com.fserver.app.presentation.screens.source.setup.shared.rememberSourceFileOpener
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreview
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun FolderScreen(
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel,
    navigateToFolder: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fileOpener = rememberSourceFileOpener()

    LaunchedEffect(Unit) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is FolderUiEffect.OpenFile -> fileOpener.open(effect.file)
            }
        }
    }

    FolderScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToFolder = navigateToFolder,
        navigateUp = navigateUp,
    )
}

@Composable
private fun FolderScreenContent(
    modifier: Modifier = Modifier,
    state: FolderState,
    onIntent: (FolderIntent) -> Unit,
    navigateToFolder: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = state.title,
                subtitle = state.summary,
                onBack = navigateUp,
                actions = {
                    IconButton(onClick = { onIntent(FolderIntent.ViewToggled) }) {
                        Icon(
                            imageVector = if (state.isMediaCollection) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Default.GridView
                            },
                            contentDescription = stringResource(R.string.files_view_mode),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            if (state.entries == null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    DkInlineSpinner()
                }
            } else {
                SourcePreview(
                    preview = state.entries,
                    onFileClick = {
                        if (it.kind == FileKindUi.Folder) {
                            navigateToFolder(it.path)
                        } else {
                            onIntent(FolderIntent.ItemClicked(it.id))
                        }
                    },
                )
            }

            if (state.showsCloudNotice) {
                DkInfoBox(
                    modifier = Modifier.padding(DkSpacing.screenPadding),
                    text = stringResource(R.string.files_folder_cloud_notice),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FolderScreenPreview() {
    FServerTheme {
        FolderScreenContent(
            state = FolderState(
                title = "Camera",
                summary = "MacOS",
                showsCloudNotice = true,
            ),
            onIntent = {},
            navigateToFolder = {},
            navigateUp = {},
        )
    }
}

@Preview(name = "List", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FolderScreenListPreview() {
    FServerTheme {
        FolderScreenContent(
            state = FolderState(
                title = "Documents",
                summary = "Windows 11",
            ),
            onIntent = {},
            navigateToFolder = {},
            navigateUp = {},
        )
    }
}
