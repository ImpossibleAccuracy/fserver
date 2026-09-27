package com.fserver.app.presentation.screens.source.request.location.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.model.HostLocationUi
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi

@Immutable
data class SyncRequestLocationState(
    val request: SyncRequestUi? = null,
    val isLoaded: Boolean = false,
    val selected: HostLocationUi = HostLocationUi.AppStorage,
    val folder: HostLocationUi.Folder? = null,
) {
    val isGone: Boolean get() = isLoaded && request == null

    val isFolderSelected: Boolean get() = selected is HostLocationUi.Folder

    val canContinue: Boolean get() = request != null
}
