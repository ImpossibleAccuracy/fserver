package com.fserver.app.presentation.screens.source.shared.preferences.model

/** What a sync keeps when a file changed on both sides. */
enum class ConflictResolutionUi {
    /** The version saved later survives; the other is gone. */
    LastWriteWins,

    /** The losing version is kept in the `.conflicts` folder next to the original. */
    KeepBoth,
}
