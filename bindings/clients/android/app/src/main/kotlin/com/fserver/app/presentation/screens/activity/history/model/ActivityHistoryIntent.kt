package com.fserver.app.presentation.screens.activity.history.model

import com.fserver.app.presentation.shared.journal.model.JournalGroupUi

sealed interface ActivityHistoryIntent {
    data class FiltersApplied(
        val groups: Set<JournalGroupUi>,
        val sourceIds: Set<String>,
        val deviceIds: Set<String>,
    ) : ActivityHistoryIntent

    data class DismissClicked(val entryId: Long) : ActivityHistoryIntent
}
