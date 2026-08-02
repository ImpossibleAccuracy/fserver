package com.fserver.app.presentation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.fserver.app.presentation.designkit.DkNavigationBar
import com.fserver.app.presentation.designkit.DkNavigationBarItem
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.TopLevelDestination
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
    val navigator = rememberAppNavigator(Destination.Onboarding)

    // The bar shows on tab roots only: a secondary screen pushed inside a tab takes the full
    // height, even though it still belongs to that tab's section.
    val isOnTopRoute = navigator.isAtSectionRoot && TopLevelDestination.entries.any {
        it.destination == navigator.activeSection
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!isOnTopRoute) return@Scaffold

            DkNavigationBar {
                TopLevelDestination.entries.forEach { tab ->
                    val isSelected = tab.destination == navigator.activeSection

                    DkNavigationBarItem(
                        label = stringResource(tab.label),
                        icon = tab.icon,
                        selected = isSelected,
                        onClick = {
                            navigator.navigate(tab.destination)
                        },
                    )
                }
            }
        },
    ) { paddings ->
        Box(
            modifier = Modifier
                .run {
                    if (isOnTopRoute) {
                        padding(bottom = paddings.calculateBottomPadding())
                    } else {
                        this
                    }
                }
        ) {
            NavHostGraph(navigator = navigator)
        }
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
