package com.fserver.app.presentation.navigation.model

import androidx.lifecycle.Lifecycle
import com.fserver.app.presentation.model.Destination

sealed interface AppRootIntent {
    data object AcceptIncomingConnection : AppRootIntent
    data object RejectIncomingConnection : AppRootIntent

    data object AcceptIncomingTransfer : AppRootIntent
    data object RejectIncomingTransfer : AppRootIntent

    data object AcceptPendingConfirmation : AppRootIntent
    data object RejectPendingConfirmation : AppRootIntent

    data class ForegroundStateChanged(
        val lifecycle: Lifecycle.State,
        val destination: Destination?,
    ) : AppRootIntent
}
