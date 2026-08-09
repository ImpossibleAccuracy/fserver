package com.fserver.app.presentation.screens.discovery.hub.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.model.NetworkCardUi

@Immutable
data class ConnectHubState(
    val network: NetworkCardUi? = null,
)
