package com.fserver.app.presentation.screens.source.setup.mode

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.setup.shared.SourceSetupParent
import com.fserver.app.presentation.screens.source.shared.popToSourcePick
import com.fserver.app.presentation.screens.source.setup.shared.sourceSetupViewModel
import com.fserver.app.presentation.screens.target.TargetDeviceSelectedResult
import timber.log.Timber

fun EntryProviderScope<Destination>.sourceModeEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Setup.Mode>(metadata = SourceSetupParent) {
        val flow = sourceSetupViewModel { navigator.popToSourcePick() } ?: return@entry

        LaunchedEffect(Unit) {
            TargetDeviceSelectedResult.collect { deviceId ->
                flow.onDeviceSelected(deviceId)

                val kind = flow.state.value.kind ?: return@collect
                val mode = flow.state.value.mode ?: return@collect

                navigator.navigate(
                    screen = Destination.Source.Setup.Conditions(
                        kind = kind,
                        mode = mode,
                    ),
                    dropping = { it is Destination.Source.Setup.Mode },
                )
            }
        }

        SourceModeScreen(
            handler = flow.mode,
            navigateNext = {
                navigator.navigate(Destination.TargetDevice)
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
