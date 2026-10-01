package com.fserver.app.presentation.navigation.model

import androidx.lifecycle.Lifecycle
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.core.files.SourceLocation

sealed interface AppRootIntent {
    data object AcceptIncomingConnection : AppRootIntent
    data object RejectIncomingConnection : AppRootIntent

    data object AcceptIncomingTransfer : AppRootIntent
    data object RejectIncomingTransfer : AppRootIntent
    data class ChangeIncomingDestination(val destination: SourceLocation.Hostable) : AppRootIntent

    data object AcceptPendingConfirmation : AppRootIntent
    data object RejectPendingConfirmation : AppRootIntent

    data class ViewFile(val file: FileBrowserUi.File) : AppRootIntent
    data object CloseFileViewer : AppRootIntent

    data class ForegroundStateChanged(
        val lifecycle: Lifecycle.State,
        val destination: Destination?,
    ) : AppRootIntent
}
