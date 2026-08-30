package com.fserver.app.presentation.screens.source.conditions

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.shared.SourceFlowParent
import com.fserver.app.presentation.screens.source.shared.isSourceFlowScreen
import com.fserver.app.presentation.screens.source.shared.popToSourcePick
import com.fserver.app.presentation.screens.source.shared.sourceFlowViewModel

fun EntryProviderScope<Destination>.sourceConditionsEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Conditions>(metadata = SourceFlowParent) { _ ->
        val flow = sourceFlowViewModel { navigator.popToSourcePick() } ?: return@entry

        SourceConditionsScreen(
            viewModel = flow,
            navigateToDone = { summary ->
                navigator.navigate(
                    screen = Destination.Source.Done(summary),
                    dropping = { it.isSourceFlowScreen },
                )
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
