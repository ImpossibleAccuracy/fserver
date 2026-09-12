package com.fserver.app.presentation.screens.activity

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.activityEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Activity> {
        ActivityScreen(
            navigateToSyncRequests = { navigator.navigate(Destination.Source.Request.List) },
        )
    }
}
