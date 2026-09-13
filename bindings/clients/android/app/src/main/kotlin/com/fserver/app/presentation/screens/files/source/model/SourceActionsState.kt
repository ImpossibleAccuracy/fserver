package com.fserver.app.presentation.screens.files.source.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi

@Immutable
data class SourceActionsState(
    val name: String = "",
    val originalName: String = "",
    val mode: SourceModeUi? = null,
    val isBusy: Boolean = false,
    val confirmingRemoval: Boolean = false,
    val isMissing: Boolean = false,
) {
    val canSave: Boolean
        get() = !isBusy && name.isNotBlank() && name != originalName

    companion object {
        val Sample = SourceActionsState(
            name = "Camera",
            originalName = "Camera",
            mode = SourceModeUi.Offload,
        )
    }
}
