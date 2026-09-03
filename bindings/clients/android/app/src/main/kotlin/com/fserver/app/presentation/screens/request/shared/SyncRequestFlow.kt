package com.fserver.app.presentation.screens.request.shared

import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

/** Every screen the answer to a peer's ask puts on the stack. Leaving drops all of them. */
val Destination.isSyncRequestScreen: Boolean
    get() = this is Destination.SyncRequest.Details ||
            this is Destination.SyncRequest.Location ||
            this is Destination.SyncRequest.Done

/**
 * Leaves the flow entirely, back to whatever screen it was started from.
 *
 * Nothing is undone by this: the request is already answered by the time it can be called.
 */
fun AppNavigator.closeSyncRequestFlow() {
    if (!popTo { !it.isSyncRequestScreen }) {
        navigate(Destination.Files.List)
    }
}
