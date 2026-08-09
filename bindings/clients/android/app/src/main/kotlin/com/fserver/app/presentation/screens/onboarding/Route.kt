package com.fserver.app.presentation.screens.onboarding

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.onboardingEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Onboarding> {
        OnboardingScreen(
            navigateToDiscovery = {
                navigator.navigateByBackstack(
                    listOf(Destination.Connect)
                )
            },
        )
    }
}
