package com.fserver.app.presentation.screens.activity.model

sealed interface ActivityIntent {
    data class RetryClicked(val transferId: String) : ActivityIntent
    data class ConflictKeepMineClicked(val conflictId: String) : ActivityIntent
    data class ConflictKeepTheirsClicked(val conflictId: String) : ActivityIntent
    data class ConflictKeepBothClicked(val conflictId: String) : ActivityIntent
    data class UndoClicked(val entryId: Long) : ActivityIntent
    data class DismissClicked(val entryId: Long) : ActivityIntent
}
