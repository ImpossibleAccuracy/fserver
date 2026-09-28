package com.fserver.app.presentation.screens.files.editor.model

sealed interface ImageEditorIntent {
    data object RotateLeft : ImageEditorIntent
    data object RotateRight : ImageEditorIntent
    data object FlipHorizontal : ImageEditorIntent
    data object Reset : ImageEditorIntent
    data object SaveRequested : ImageEditorIntent
    data object RetryRequested : ImageEditorIntent
}
