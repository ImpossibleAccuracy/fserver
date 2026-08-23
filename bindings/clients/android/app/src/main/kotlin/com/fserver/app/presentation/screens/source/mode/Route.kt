package com.fserver.app.presentation.screens.source.mode

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.TargetPurpose
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.sourceModeEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Mode> { key ->
        SourceModeScreen(
            key = key,
            navigateToTarget = { mode ->
                navigator.navigate(
                    Destination.TargetDevice(
                        TargetPurpose.ConfigureSource(kind = key.kind, mode = mode)
                    )
                )
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
