package com.fserver.app.presentation.screens.target

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.receiveAsFlow

private val TargetDeviceSelectedBus = Channel<String>(Channel.BUFFERED)
val TargetDeviceSelectedResult = TargetDeviceSelectedBus.receiveAsFlow()

fun EntryProviderScope<Destination>.targetDeviceEntry(
    navigator: AppNavigator,
) {
    entry<Destination.TargetDevice> {
        TargetDeviceScreen(
            navigateToConnect = { navigator.navigate(Destination.Connect) },
            answer = {
                if (TargetDeviceSelectedBus.trySend(it).isSuccess) {
                    navigator.navigateUp()
                }
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
