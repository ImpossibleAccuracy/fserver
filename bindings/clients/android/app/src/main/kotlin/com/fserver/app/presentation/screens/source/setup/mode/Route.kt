package com.fserver.app.presentation.screens.source.setup.mode

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.ResultEffect
import com.fserver.app.presentation.screens.discovery.connect.model.DeviceSelection
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupParent
import com.fserver.app.presentation.screens.source.shared.popToSourcePick
import com.fserver.app.presentation.screens.source.setup.shared.sourceSetupViewModel

fun EntryProviderScope<Destination>.sourceModeEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Setup.Mode>(metadata = SourceSetupParent) {
        val flow = sourceSetupViewModel { navigator.popToSourcePick() } ?: return@entry

        // The target is not a step this flow owns: it asks the app-wide picker and picks the
        // answer up here, which is why the mode screen is what the user comes back to.
        ResultEffect<DeviceSelection> { selection ->
            flow.selectTarget(selection.deviceId)

            val state = flow.state.value
            val kind = state.kind
            val mode = state.mode

            if (kind != null && mode != null) {
                navigator.navigate(
                    screen = Destination.Source.Setup.Conditions(kind = kind, mode = mode),
                    dropping = { it is Destination.Source.Setup.Mode },
                )
            }
        }

        SourceModeScreen(
            handler = flow.mode,
            // A flow opened from a device already knows where the source goes, so it walks past
            // the picker instead of asking a question with one answer.
            navigateNext = {
                val state = flow.state.value
                val kind = state.kind
                val mode = state.mode

                if (state.targetDeviceId != null && kind != null && mode != null) {
                    navigator.navigate(Destination.Source.Setup.Conditions(kind = kind, mode = mode))
                } else {
                    navigator.navigate(Destination.Connect)
                }
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
