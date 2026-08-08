package com.fserver.app.presentation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.fserver.app.presentation.composable.AppStyling
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.rememberAppNavigator
import com.fserver.app.presentation.navigation.scene.BottomSheetSceneStrategy
import com.fserver.app.presentation.screens.diagnostics.diagnosticEntry
import com.fserver.app.presentation.screens.discovery.automatic.deviceDiscoveryEntry
import com.fserver.app.presentation.screens.discovery.manual.manualAddressEntry
import com.fserver.app.presentation.screens.discovery.qr.qrScanEntry
import com.fserver.app.presentation.screens.files.list.filesListEntry
import com.fserver.app.presentation.screens.files.picker.filesPickerEntry
import com.fserver.app.presentation.screens.onboarding.onboardingEntry
import com.fserver.app.presentation.screens.pairing.pairingEntry
import com.fserver.app.presentation.screens.settings.settingsEntry
import com.fserver.app.presentation.screens.transfers.transfersEntry


@Composable
fun FServerApp() {
//    val navigator = rememberAppNavigator(Destination.Onboarding)
    val navigator = rememberAppNavigator(Destination.DeviceDiscovery)

    AppStyling(
        navigator = navigator,
    ) {
        NavHostGraph(navigator = navigator)
    }
}

@Composable
private fun NavHostGraph(navigator: AppNavigator) {
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<Destination>() }

    NavDisplay(
        backStack = navigator.activeBackStack,
        onBack = { navigator.navigateUp() },
        sceneStrategies = listOf(bottomSheetStrategy),
        // TODO: add animations
        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        entryProvider = entryProvider {
            onboardingEntry(navigator)
            deviceDiscoveryEntry(navigator)
            qrScanEntry(navigator)
            manualAddressEntry(navigator)
            pairingEntry(navigator)
            transfersEntry()
            settingsEntry(navigator)
            diagnosticEntry(navigator)

            filesListEntry(navigator)
            filesPickerEntry(navigator)
        },
    )
}
