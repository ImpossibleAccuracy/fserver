package com.fserver.app.presentation.screens.files.editor.model

import androidx.compose.runtime.Immutable
import com.attafitamim.krop.core.crop.CropState
import com.attafitamim.krop.core.crop.ImgTransform

@Immutable
data class ImageEditorState(
    val fileName: String = "",
    val status: StatusUi = StatusUi.Loading,
    val crop: CropState? = null,
) {
    val canEdit: Boolean
        get() = crop != null && status == StatusUi.Ready

    val hasChanges: Boolean
        get() = crop != null && (crop.transform != ImgTransform.Identity || crop.region != crop.defaultRegion)

    enum class StatusUi { Loading, Ready, Saving, Failed }
}
