package com.fserver.app.presentation.screens.files.folder.model

import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi

sealed interface FolderUiEffect {
    data class OpenFile(val file: SourcePreviewUi.File) : FolderUiEffect
}
