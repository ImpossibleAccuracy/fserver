package com.fserver.app.presentation.screens.onboarding

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.onboardingEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Onboarding> {
        OnboardingScreen(
            navigateToConnect = {
                navigator.navigate(Destination.Connect)
            },
            navigateToSourcePick = {
                navigator.navigate(Destination.Source.Setup.Pick)
            },
            navigateToFiles = {
                navigator.navigate(Destination.Files.List)
            },
        )
    }
}
