package com.fserver.app.presentation.screens.activity.history.model

import com.fserver.core.network.device.model.DeviceKind
import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.shared.journal.model.JournalEntryUi
import com.fserver.app.presentation.shared.journal.model.JournalGroupUi
import java.time.LocalDate

@Immutable
data class ActivityHistoryState(
    val isLoading: Boolean = true,
    val groups: Set<JournalGroupUi> = emptySet(),
    val sources: List<SourceUi> = emptyList(),
    val sourceIds: Set<String> = emptySet(),
    val devices: List<DeviceUi> = emptyList(),
    val deviceIds: Set<String> = emptySet(),
    val days: List<DayUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !isLoading && days.isEmpty()

    val isFiltered: Boolean
        get() = groups.isNotEmpty() || sourceIds.isNotEmpty() || deviceIds.isNotEmpty()

    @Immutable
    data class SourceUi(
        val id: String,
        val label: String,
    )

    @Immutable
    data class DeviceUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
    )

    @Immutable
    data class DayUi(
        val date: LocalDate,
        val entries: List<JournalEntryUi>,
    )

    companion object {
        val Sample = ActivityHistoryState(
            isLoading = false,
            sources = listOf(SourceUi("documents", "Documents"), SourceUi("camera", "Camera")),
            devices = listOf(DeviceUi("laptop", "Laptop", DeviceKind.Laptop)),
            days = listOf(DayUi(LocalDate.of(2026, 9, 21), JournalEntryUi.Samples)),
        )
    }
}
