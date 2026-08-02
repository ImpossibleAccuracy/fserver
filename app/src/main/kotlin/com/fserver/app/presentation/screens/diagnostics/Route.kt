package com.fserver.app.presentation.screens.diagnostics

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.diagnosticEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Diagnostics> {
        DiagnosticsScreen(
            navigateUp = navigator::navigateUp,
        )
    }
}
