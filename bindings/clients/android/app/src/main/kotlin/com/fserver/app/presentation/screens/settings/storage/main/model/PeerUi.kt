package com.fserver.app.presentation.screens.settings.storage.main.model

import androidx.compose.runtime.Immutable
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class PeerUi(
    val name: String = "",
    val kind: DeviceKind? = null,
)
