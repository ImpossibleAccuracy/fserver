package com.fserver.app.presentation.screens.discovery.manual

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.scene.BottomSheetSceneStrategy

@OptIn(ExperimentalMaterial3Api::class)
fun EntryProviderScope<Destination>.manualAddressEntry(
    navigator: AppNavigator,
) {
    entry<Destination.ManualAddress>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) {
        ManualAddressScreen(
            // The sheet is dismissed before the push, not left underneath it: coming back
            // from confirmation should land on the discovery list, not on a half-filled form.
            navigateToPairing = { target ->
                navigator.navigateUp()
                navigator.navigate(
                    Destination.Pairing(
                        peerLocator = target
                    )
                )
            },
        )
    }
}
