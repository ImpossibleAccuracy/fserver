package com.fserver.app.presentation.screens.request.details.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.request.shared.model.SyncRequestUi

@Immutable
data class SyncRequestDetailsState(
    val request: SyncRequestUi? = null,
    val isLoaded: Boolean = false,
    val isAnswering: Boolean = false,
) {
    val isGone: Boolean get() = isLoaded && request == null

    val canAnswer: Boolean get() = request != null && !isAnswering
}
