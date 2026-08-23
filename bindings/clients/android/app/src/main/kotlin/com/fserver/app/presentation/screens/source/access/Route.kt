package com.fserver.app.presentation.screens.source.access

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

fun EntryProviderScope<Destination>.sourceAccessEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Access> { key ->
        SourceAccessScreen(
            key = key,
            navigateToMode = { access ->
                navigator.navigate(Destination.Source.Mode(kind = key.kind, access = access))
            },
            navigateToSourcePick = {
                // "Back to source choice" is a return, not a new screen: the pick is still under
                // this one unless the flow was entered straight into a branch.
                if (!navigator.popTo { it is Destination.Source.Pick }) {
                    navigator.navigate(Destination.Source.Pick)
                }
            },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
