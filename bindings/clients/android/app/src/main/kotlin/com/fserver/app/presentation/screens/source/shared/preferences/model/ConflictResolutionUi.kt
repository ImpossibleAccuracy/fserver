package com.fserver.app.presentation.screens.source.shared.preferences.model

/** What a sync does when a file changed on both sides. */
enum class ConflictResolutionUi {
    /** Each side keeps its own version until the user picks one. */
    Ask,

    /** The version saved later survives; the other is gone. */
    LastWriteWins,
}
