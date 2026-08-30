package com.fserver.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.savedstate.compose.LocalSavedStateRegistryOwner

/**
 * The [ViewModelStoreOwner] of the entry a flow is rooted at, for the entries above it.
 *
 * Only entries carrying [SharedViewModelStoreNavEntryDecorator.parent] metadata get one; reading
 * it anywhere else is a wiring mistake, so it fails loudly rather than silently handing back the
 * entry's own store.
 */
val LocalSharedViewModelStoreOwner = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("No LocalSharedViewModelStoreOwner: this entry declares no parent() metadata.")
}

/**
 * [androidx.lifecycle.viewmodel.navigation3.ViewModelStoreNavEntryDecorator] plus one extra
 * store: an entry may name another entry as its parent and reach that entry's ViewModels.
 *
 * A ViewModel scoped that way lives exactly as long as the flow — it is created when the first
 * screen of the flow asks for it and cleared when the parent entry is popped, which is what
 * separates it from an activity-scoped one that would survive into the next run of the flow.
 *
 * A ViewModel is still the wrong place for anything that has to outlive the process: the store
 * dies with it, and only what a `SavedStateHandle` holds comes back.
 */
class SharedViewModelStoreNavEntryDecorator<T : Any>(
    viewModelStoreProvider: ViewModelStoreProvider,
) : NavEntryDecorator<T>(
    onPop = { key -> viewModelStoreProvider.clearKey(key) },
    decorate = { entry ->
        val localOwner = rememberViewModelStoreOwner(
            entry.contentKey,
            viewModelStoreProvider,
            savedStateRegistryOwner = LocalSavedStateRegistryOwner.current,
        )

        val values: MutableList<ProvidedValue<*>> =
            mutableListOf(LocalViewModelStoreOwner provides localOwner)

        entry.metadata[ParentKey]?.let { parentContentKey ->
            val parentOwner = rememberViewModelStoreOwner(
                parentContentKey,
                viewModelStoreProvider,
                savedStateRegistryOwner = LocalSavedStateRegistryOwner.current,
            )
            values += LocalSharedViewModelStoreOwner provides parentOwner
        }

        CompositionLocalProvider(values = values.toTypedArray()) { entry.Content() }
    },
) {
    companion object {
        /**
         * Metadata declaring that this entry shares [contentKey]'s ViewModel store.
         *
         * [contentKey] must be the parent [NavEntry.contentKey], which defaults to the key's
         * `toString()` — Navigation 3 keeps the function producing it internal, so
         * [contentKeyOf] mirrors it rather than calling it.
         */
        fun parent(contentKey: Any): Map<String, Any> = metadata { put(ParentKey, contentKey) }

        /** What Navigation 3 uses as an entry's `contentKey` when none is given. */
        fun contentKeyOf(key: Any): Any = key.toString()

        object ParentKey : NavMetadataKey<Any>
    }
}

@Composable
fun <T : Any> rememberSharedViewModelStoreNavEntryDecorator(
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    },
): SharedViewModelStoreNavEntryDecorator<T> {
    val viewModelStoreProvider = rememberViewModelStoreProvider(parent = viewModelStoreOwner)
    return remember(viewModelStoreProvider) {
        SharedViewModelStoreNavEntryDecorator(viewModelStoreProvider)
    }
}
