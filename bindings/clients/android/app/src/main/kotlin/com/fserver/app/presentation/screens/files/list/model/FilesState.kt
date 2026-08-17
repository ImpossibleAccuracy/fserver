package com.fserver.app.presentation.screens.files.list.model

import com.fserver.app.presentation.composable.shared.FileUi
import com.fserver.app.presentation.composable.shared.FilesViewModeUi
import com.fserver.app.presentation.composable.shared.TreeNodeUi

data class FilesState(
    val serverName: String = "",
    val breadcrumb: String = "",
    val files: List<FileUi> = emptyList(),
    val gridTiles: List<FileUi> = emptyList(),
    val tree: List<TreeNodeUi> = emptyList(),
    val itemCount: Int = 0,
    val viewMode: FilesViewModeUi = FilesViewModeUi.List,
)