package com.fserver.app.presentation.screens.source.access

import androidx.navigation3.runtime.EntryProviderScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.screens.source.shared.SourceFlowParent
import com.fserver.app.presentation.screens.source.shared.popToSourcePick
import com.fserver.app.presentation.screens.source.shared.sourceFlowViewModel

fun EntryProviderScope<Destination>.sourceAccessEntry(
    navigator: AppNavigator,
) {
    entry<Destination.Source.Access>(metadata = SourceFlowParent) { key ->
        val flow = sourceFlowViewModel { navigator.popToSourcePick() } ?: return@entry

        SourceAccessScreen(
            handler = flow.access,
            // Access is answered once granted, and re-answering it means re-running the system
            // dialog from the pick — not stepping back into an explainer for a grant already held.
            navigateToMode = { access ->
                navigator.navigate(
                    screen = Destination.Source.Mode(kind = key.kind, access = access),
                    dropping = { it is Destination.Source.Access },
                )
            },
            // The pick is always the entry directly below this one, so both ways back are a pop.
            navigateToSourcePick = { navigator.navigateUp() },
            navigateUp = { navigator.navigateUp() },
        )
    }
}
