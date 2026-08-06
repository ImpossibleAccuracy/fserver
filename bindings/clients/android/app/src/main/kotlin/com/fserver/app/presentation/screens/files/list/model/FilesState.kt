package com.fserver.app.presentation.screens.files.list.model

import com.fserver.app.presentation.model.FileUi
import com.fserver.app.presentation.model.FilesViewModeUi
import com.fserver.app.presentation.model.IncomingRequestUi
import com.fserver.app.presentation.model.TreeNodeUi

data class FilesState(
    val serverName: String = "",
    val breadcrumb: String = "",
    val files: List<FileUi> = emptyList(),
    val gridTiles: List<FileUi> = emptyList(),
    val tree: List<TreeNodeUi> = emptyList(),
    val itemCount: Int = 0,
    val viewMode: FilesViewModeUi = FilesViewModeUi.List,
    /** A transfer offered to the user; non-null while the receive sheet is up. */
    val incomingRequest: IncomingRequestUi? = null,
)