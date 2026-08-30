package com.fserver.app.presentation.screens.source.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator
import com.fserver.app.presentation.navigation.LocalSharedViewModelStoreOwner
import com.fserver.app.presentation.navigation.SharedViewModelStoreNavEntryDecorator
import org.koin.androidx.compose.koinViewModel

/**
 * Metadata every screen of the send flow above [Destination.Source.Pick] declares, so all five
 * reach the same [SourceFlowViewModel] and it is cleared when the pick is popped.
 */
val SourceFlowParent: Map<String, Any> = SharedViewModelStoreNavEntryDecorator.parent(
    SharedViewModelStoreNavEntryDecorator.contentKeyOf(Destination.Source.Pick)
)

/** The flow's ViewModel as seen from the entry that owns its store — the pick. */
@Composable
fun ownedSourceFlowViewModel(): SourceFlowViewModel = koinViewModel()

/**
 * The flow's ViewModel as seen from a screen above the pick, or null when there is no flow to
 * continue.
 *
 * Null means the store was rebuilt without the answers that filled it — a process death, whose
 * restored back stack still names a screen halfway through the flow. There is no honest way to
 * resume from there, so [onRestart] sends the user back to the pick and the caller renders
 * nothing in the meantime.
 */
@Composable
fun sourceFlowViewModel(onRestart: () -> Unit): SourceFlowViewModel? {
    val flow: SourceFlowViewModel =
        koinViewModel(viewModelStoreOwner = LocalSharedViewModelStoreOwner.current)
    val isStarted by flow.isStarted.collectAsStateWithLifecycle()

    if (!isStarted) {
        LaunchedEffect(Unit) { onRestart() }
        return null
    }

    return flow
}

/**
 * Back to the pick, dropping whatever half-finished flow stood above it. Falls back to pushing
 * the pick when it is not on the stack at all, which is the process-death case.
 */
fun AppNavigator.popToSourcePick() {
    if (!popTo { it is Destination.Source.Pick }) {
        navigate(Destination.Source.Pick)
    }
}

/**
 * Every screen the send-source flow puts on the stack, including the target picker in the
 * middle of it. Reaching the summary drops all of them at once.
 */
val Destination.isSourceFlowScreen: Boolean
    get() = this is Destination.Source.Pick ||
            this is Destination.Source.Access ||
            this is Destination.Source.Mode ||
            this is Destination.Source.Conditions ||
            this is Destination.TargetDevice
