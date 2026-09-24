package com.fserver.app.presentation.screens.files.folder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.fserver.app.presentation.shared.browser.FileBrowser
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.SampleFiles
import com.fserver.app.presentation.shared.viewer.LocalFileOpener
import com.fserver.app.presentation.theme.FServerTheme

@Composable
fun FolderScreen(
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel,
    navigateToFolder: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fileOpener = LocalFileOpener.current

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
                    if (state.showsSort) {
                        SortAction(
                            sort = state.sort,
                            ascending = state.sortAscending,
                            onSelect = { onIntent(FolderIntent.SortSelected(it)) },
                        )
                    }

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
                FileBrowser(
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

@Composable
private fun SortAction(
    modifier: Modifier = Modifier,
    sort: FolderState.SortUi,
    ascending: Boolean,
    onSelect: (FolderState.SortUi) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.files_sort),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            FolderState.SortUi.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (option == sort) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    trailingIcon = {
                        if (option == sort) {
                            Icon(
                                modifier = Modifier.size(16.dp),
                                imageVector = if (ascending) {
                                    Icons.Default.ArrowUpward
                                } else {
                                    Icons.Default.ArrowDownward
                                },
                                contentDescription = stringResource(
                                    if (ascending) {
                                        R.string.files_sort_ascending
                                    } else {
                                        R.string.files_sort_descending
                                    }
                                ),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@get:StringRes
private val FolderState.SortUi.labelRes: Int
    get() = when (this) {
        FolderState.SortUi.Name -> R.string.files_sort_name
        FolderState.SortUi.Date -> R.string.files_sort_date
        FolderState.SortUi.Size -> R.string.files_sort_size
        FolderState.SortUi.Kind -> R.string.files_sort_kind
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun FolderScreenPreview() {
    FServerTheme {
        FolderScreenContent(
            state = FolderState(
                title = "Camera",
                summary = "MacOS",
                entries = FileBrowserUi.Gallery(FileBrowserUi.SampleFiles),
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
                entries = FileBrowserUi.PlainList(FileBrowserUi.SampleFiles),
                sort = FolderState.SortUi.Size,
                sortAscending = false,
            ),
            onIntent = {},
            navigateToFolder = {},
            navigateUp = {},
        )
    }
}
