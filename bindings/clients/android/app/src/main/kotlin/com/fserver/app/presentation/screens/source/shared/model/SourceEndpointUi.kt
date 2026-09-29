package com.fserver.app.presentation.screens.source.shared.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.model.UiText
import com.fserver.core.network.device.model.DeviceKind

/** One end of a source: the device, and optionally where on it the files live. */
@Immutable
data class SourceEndpointUi(
    val name: String = "",
    val detail: UiText? = null,
    val deviceKind: DeviceKind? = null,
)
