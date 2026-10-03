package com.fserver.app.presentation.screens.activity.history

import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.composable.model.peerOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.localDate
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.activity.history.model.ActivityHistoryIntent
import com.fserver.app.presentation.screens.activity.history.model.ActivityHistoryState
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.app.presentation.shared.journal.journalFeed
import com.fserver.app.presentation.shared.journal.model.JournalEntryUi
import com.fserver.app.presentation.shared.journal.model.JournalGroupUi
import com.fserver.app.util.stateInScreen
import com.fserver.core.journal.ActivityJournal
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ActivityHistoryViewModel(
    key: Destination.Activity.History,
    private val journal: ActivityJournal,
    devicesRepository: DevicesRepository,
    registeredSources: RegisteredSourcesRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val filters = MutableStateFlow(Filters(groups = emptySet(), sourceIds = setOfNotNull(key.sourceId), deviceIds = setOfNotNull(key.deviceId)))

    val state: StateFlow<ActivityHistoryState> = combine(
        journal.journalFeed(devicesRepository, registeredSources),
        registeredSources.sources,
        devicesRepository.peers(),
        filters,
    ) { entries, sources, peers, filters ->
        ActivityHistoryState(
            isLoading = false,
            groups = filters.groups,
            sourceIds = filters.sourceIds,
            devices = (entries.mapNotNull { it.deviceId } + filters.deviceIds)
                .distinct()
                .map { peers.peerOf(it) }
                .map { ActivityHistoryState.DeviceUi(it.id, it.name, it.kind) }
                .sortedBy { it.name },
            deviceIds = filters.deviceIds,
            sources = sources.map { ActivityHistoryState.SourceUi(it.id, it.label) },
            days = entries
                .filter { filters.matches(it) }
                .groupBy { it.at.localDate() }
                .map { (date, entries) -> ActivityHistoryState.DayUi(date, entries) },
        )
    }.stateInScreen(viewModelScope, ActivityHistoryState())

    fun onIntent(intent: ActivityHistoryIntent) {
        when (intent) {
            is ActivityHistoryIntent.FiltersApplied -> filters.value = Filters(intent.groups, intent.sourceIds, intent.deviceIds)
            is ActivityHistoryIntent.DismissClicked -> dismiss(intent.entryId)
        }
    }

    private fun dismiss(entryId: Long) {
        viewModelScope.launch {
            journal.solve(entryId).onFailure { reporter.report(it, "Could not dismiss journal entry $entryId") }
        }
    }
}

private data class Filters(
    val groups: Set<JournalGroupUi>,
    val sourceIds: Set<String>,
    val deviceIds: Set<String>,
) {
    fun matches(entry: JournalEntryUi): Boolean =
        (groups.isEmpty() || entry.group in groups) &&
            (sourceIds.isEmpty() || entry.sourceId in sourceIds) &&
            (deviceIds.isEmpty() || entry.deviceId in deviceIds)
}
