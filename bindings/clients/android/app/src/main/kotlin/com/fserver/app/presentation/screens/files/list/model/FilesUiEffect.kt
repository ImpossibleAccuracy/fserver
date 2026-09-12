package com.fserver.app.presentation.screens.files.list.model

import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi

sealed interface FilesUiEffect {
    data class OpenFile(val file: SourcePreviewUi.File) : FilesUiEffect
}
