package com.fserver.app.presentation.screens.activity.model

sealed interface ActivityIntent {
    data object ClearClicked : ActivityIntent
    data class RetryClicked(val transferId: String) : ActivityIntent
    data class ConflictCompareClicked(val conflictId: String) : ActivityIntent
    data class ConflictKeepMineClicked(val conflictId: String) : ActivityIntent
    data class UndoClicked(val entryId: String) : ActivityIntent
    data object FullHistoryClicked : ActivityIntent
}
