package com.fserver.app.presentation.navigation.model

import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.IncomingConnectionUi
import com.fserver.app.presentation.model.PendingConfirmationUi

data class AppRootState(
    val startDestination: Destination,
    val incomingConnection: IncomingConnectionUi?,
    val pendingConfirmation: PendingConfirmationUi?,
)
