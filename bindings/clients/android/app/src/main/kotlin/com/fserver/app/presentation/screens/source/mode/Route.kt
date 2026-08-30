package com.fserver.app.presentation.screens.source.mode

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.shared.SourceFlowParent
import com.fserver.app.presentation.screens.source.shared.popToSourcePick
import com.fserver.app.presentation.screens.source.shared.sourceFlowViewModel
import com.fserver.app.presentation.screens.target.TargetDeviceSelectedResult
import timber.log.Timber

fun EntryProviderScope<Destination>.sourceModeEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Mode>(metadata = SourceFlowParent) {
        val flow = sourceFlowViewModel { navigator.popToSourcePick() } ?: return@entry

        LaunchedEffect(Unit) {
            Timber.i("SourceModeScreen: LaunchedEffect: collecting TargetDeviceSelectedResult")
            TargetDeviceSelectedResult.collect { deviceId ->
                Timber.d("SourceModeScreen: TargetDeviceSelectedResult collected: deviceId=$deviceId")
                val kind = flow.modeState.value?.kind ?: return@collect
                val mode = flow.modeState.value?.selected ?: return@collect

                flow.onDeviceSelected(deviceId)
                navigator.navigate(
                    screen = Destination.Source.Conditions(
                        kind = kind,
                        mode = mode,
                    ),
                    dropping = { it is Destination.Source.Mode },
                )
            }
        }

        SourceModeScreen(
            viewModel = flow,
            navigateNext = {
                navigator.navigate(Destination.TargetDevice)
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
