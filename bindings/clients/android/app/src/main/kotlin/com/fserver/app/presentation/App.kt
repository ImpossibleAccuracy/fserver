package com.fserver.app.presentation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.fserver.app.presentation.composable.AppStyling
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.rememberAppNavigator
import com.fserver.app.presentation.screens.diagnostics.diagnosticEntry
import com.fserver.app.presentation.screens.discovery.deviceDiscoveryEntry
import com.fserver.app.presentation.screens.files.filesEntry
import com.fserver.app.presentation.screens.onboarding.onboardingEntry
import com.fserver.app.presentation.screens.pairing.pairingEntry
import com.fserver.app.presentation.screens.profile.serverProfileEntry
import com.fserver.app.presentation.screens.qr.qrScanEntry
import com.fserver.app.presentation.screens.settings.settingsEntry
import com.fserver.app.presentation.screens.transfers.transfersEntry


@Composable
fun FServerApp() {
//    val navigator = rememberAppNavigator(Destination.Onboarding)
    val navigator = rememberAppNavigator(Destination.Files)

    AppStyling(
        navigator = navigator,
    ) {
        NavHostGraph(navigator = navigator)
    }
}

@Composable
private fun NavHostGraph(navigator: AppNavigator) {
    val entryProvider = entryProvider {
        onboardingEntry(navigator)
        deviceDiscoveryEntry(navigator)
        pairingEntry(navigator)
        qrScanEntry(navigator)
        serverProfileEntry(navigator)
        filesEntry(navigator)
        transfersEntry()
        settingsEntry(navigator)
        diagnosticEntry(navigator)
    }

    NavDisplay(
        backStack = navigator.activeBackStack,
        onBack = { navigator.navigateUp() },
        // TODO: add animations
        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        entryProvider = entryProvider,
    )
}
