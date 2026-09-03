package com.fserver.app.presentation.screens.source.upload.model

sealed interface SourceUploadUiEffect {
    /** The first pass is through — what is left is the summary. */
    data object NavigateToDone : SourceUploadUiEffect
}
