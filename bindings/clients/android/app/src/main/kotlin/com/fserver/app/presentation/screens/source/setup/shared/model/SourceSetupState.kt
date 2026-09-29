package com.fserver.app.presentation.screens.source.setup.shared.model

import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi

data class SourceSetupState(
    val kind: SourceKindUi? = null,
    val access: SourceAccessUi = SourceAccessUi.Full,
    val source: PickedSourceUi? = null,
    val mode: SourceModeUi? = null,
    val targetDeviceId: String? = null,
    val sourceId: String? = null,
)
