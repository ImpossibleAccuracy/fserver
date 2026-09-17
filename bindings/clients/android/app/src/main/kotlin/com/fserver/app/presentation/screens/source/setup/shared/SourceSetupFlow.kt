package com.fserver.app.presentation.screens.source.setup.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.LocalSharedViewModelStoreOwner
import com.fserver.app.presentation.navigation.SharedViewModelStoreNavEntryDecorator
import com.fserver.app.presentation.screens.source.shared.isSourceScreen
import org.koin.androidx.compose.koinViewModel

/**
 * The pick's `contentKey`, fixed rather than derived from the key.
 *
 * [Destination.Source.Setup.Pick] carries the target device, so its `toString()` — the default
 * content key — differs per flow, and the screens above it could no longer name one store.
 */
val SourceSetupContentKey: Any = "source-setup-pick"

/**
 * Metadata every setup screen above [Destination.Source.Setup.Pick] declares, so all of them reach
 * the same [SourceSetupViewModel] and it is cleared when the pick is popped.
 */
val SourceSetupParent: Map<String, Any> =
    SharedViewModelStoreNavEntryDecorator.parent(SourceSetupContentKey)

/** The flow's ViewModel as seen from the entry that owns its store — the pick. */
@Composable
fun ownedSourceSetupViewModel(): SourceSetupViewModel = koinViewModel()

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
fun sourceSetupViewModel(onRestart: () -> Unit): SourceSetupViewModel? {
    val flow: SourceSetupViewModel =
        koinViewModel(viewModelStoreOwner = LocalSharedViewModelStoreOwner.current)
    val isStarted by flow.isStarted.collectAsStateWithLifecycle()

    if (!isStarted) {
        LaunchedEffect(Unit) { onRestart() }
        return null
    }

    return flow
}

/**
 * The screens a later step may drop behind it — everything but the pick.
 *
 * The pick stays on the stack for the whole flow because it owns the shared ViewModel: dropping
 * it clears the store, and the screens still to come would have nothing left to read.
 */
val Destination.isAnsweredSourceSetupScreen: Boolean
    get() = isSourceScreen && this !is Destination.Source.Setup.Pick
