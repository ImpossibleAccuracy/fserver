package com.fserver.app.presentation.navigation.model

import com.fserver.app.presentation.navigation.TopLevelDestination
import com.fserver.app.presentation.composable.IncomingConnectionUi
import com.fserver.app.presentation.composable.IncomingRequestUi
import com.fserver.app.presentation.composable.PendingConfirmationUi
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi

data class AppRootState(
    val startDestination: Destination,
    val incomingConnection: IncomingConnectionUi?,
    val pendingConfirmation: PendingConfirmationUi?,
    val incomingTransfer: IncomingRequestUi?,
    val viewedFile: FileBrowserUi.File? = null,
    val badgedTabs: Set<TopLevelDestination> = emptySet(),
)
