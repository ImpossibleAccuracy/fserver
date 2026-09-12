package com.fserver.app.presentation.screens.source.list.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi

@Immutable
data class SyncRequestListState(
    val requests: List<SyncRequestUi> = emptyList(),
    val isAnswering: Boolean = false,
) {
    val isEmpty: Boolean get() = requests.isEmpty()

    val canAnswer: Boolean get() = !isAnswering && requests.isNotEmpty()

    companion object {
        val SampleRequests = listOf(
            SyncRequestUi(
                sourceId = "4c81",
                deviceName = "Laptop",
                label = "Projects",
                mode = SourceModeUi.Sync,
            ),
            SyncRequestUi(
                sourceId = "1d39",
                deviceName = "Home PC",
                label = "Downloads/Exchange",
                mode = SourceModeUi.AutoUpload,
            ),
            SyncRequestUi(
                sourceId = "9f2a",
                deviceName = "Server",
                label = "Camera",
                mode = SourceModeUi.Offload,
            ),
        )
    }
}
