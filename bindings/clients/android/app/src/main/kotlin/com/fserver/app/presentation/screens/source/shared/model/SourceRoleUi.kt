package com.fserver.app.presentation.screens.source.shared.model

/**
 * Which half of a source this device is — the one thing the screens shared by both flows branch
 * on. It is decided when the source is registered and never changes afterwards.
 */
enum class SourceRoleUi {
    /** Registered the source here and asked the peer to host the other half. */
    Initiator,

    /** Accepted a peer's ask, and holds what arrives in the location the user picked. */
    Follower,
}
