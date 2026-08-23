package com.fserver.app.presentation.screens.files.list.model

import com.fserver.app.presentation.composable.model.FileUi
import com.fserver.app.presentation.composable.model.FilesViewModeUi
import com.fserver.app.presentation.composable.model.TreeNodeUi

data class FilesState(
    val serverName: String = "",
    val breadcrumb: String = "",
    val files: List<FileUi> = emptyList(),
    val gridTiles: List<FileUi> = emptyList(),
    val tree: List<TreeNodeUi> = emptyList(),
    val itemCount: Int = 0,
    val viewMode: FilesViewModeUi = FilesViewModeUi.List,
) {
    /**
     * Nothing is connected and nothing is shared, so there is no tree to show in any of the
     * three shapes. The screen swaps to the two ways out rather than to an empty list.
     */
    val isEmpty: Boolean
        get() = files.isEmpty() && gridTiles.isEmpty() && tree.isEmpty()
}
