package com.fserver.app.presentation.screens.source.shared

import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.navigation.AppNavigator

/**
 * Every screen either half of a source puts on the stack — setting one up here, or answering a
 * peer's ask. Leaving drops all of them at once, whichever half the user is on.
 */
val Destination.isSourceScreen: Boolean
    get() = this is Destination.Source.Setup.Pick ||
            this is Destination.Source.Setup.Access ||
            this is Destination.Source.Setup.Mode ||
            this is Destination.Source.Setup.Conditions ||
            this is Destination.Source.Request.Details ||
            this is Destination.Source.Request.Location ||
            this is Destination.Source.Progress ||
            this is Destination.Source.Done

/**
 * Leaves the flow entirely, back to whatever screen it was started from.
 *
 * Nothing is undone by this: the source is registered by the time either half can call it, and
 * the engine goes on without a screen open.
 */
fun AppNavigator.closeSourceFlow() {
    if (!popTo { !it.isSourceScreen }) {
        navigate(Destination.Files.List)
    }
}

/**
 * Back to the pick, dropping whatever half-finished flow stood above it. Falls back to pushing
 * the pick when it is not on the stack at all — the process-death case, and the one the done
 * screen takes when the source was a peer's rather than this device's.
 */
fun AppNavigator.popToSourcePick() {
    if (!popTo { it is Destination.Source.Setup.Pick }) {
        navigate(Destination.Source.Setup.Pick())
    }
}
