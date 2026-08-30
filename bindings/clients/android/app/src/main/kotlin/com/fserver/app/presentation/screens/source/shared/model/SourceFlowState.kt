package com.fserver.app.presentation.screens.source.shared.model

data class SourceFlowState(
    val kind: SourceKindUi? = null,
    val access: SourceAccessUi = SourceAccessUi.Full,
    val source: PickedSourceUi? = null,
    val mode: SourceModeUi? = null,
    val targetDeviceId: String? = null,
    val conditions: SavedConditions? = null,
) {
    data class SavedConditions(
        val olderThanDays: Int,
    )
}
