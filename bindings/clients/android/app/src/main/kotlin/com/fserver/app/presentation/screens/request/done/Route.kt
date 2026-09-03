package com.fserver.app.presentation.screens.request.done

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.request.shared.closeSyncRequestFlow

fun EntryProviderScope<Destination>.syncRequestDoneEntry(
    navigator: AppNavigator,
) {
    entry<Destination.SyncRequest.Done> { key ->
        SyncRequestDoneScreen(
            key = key,
            navigateToFiles = { navigator.closeSyncRequestFlow() },
        )
    }
}
