package com.fserver.app.presentation.screens.discovery.hub.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.discovery.shared.NetworkCardUi
import com.fserver.app.presentation.permission.RequirementAction

@Immutable
data class ConnectHubState(
    val network: NetworkCardUi? = null,
    /** What clears the way to naming the network; null when nothing is in the way. */
    val networkAction: RequirementAction? = null,
)
