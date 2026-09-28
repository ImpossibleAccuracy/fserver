package com.fserver.app.presentation.screens.files.editor.model

sealed interface ImageEditorUiEffect {
    data object NavigateBack : ImageEditorUiEffect
}
