package com.fserver.app.presentation.screens.activity.history

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.activityHistoryEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Activity.History> { key ->
        ActivityHistoryScreen(
            key = key,
            navigateUp = navigator::navigateUp,
        )
    }
}
