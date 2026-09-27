package com.fserver.app.presentation.screens.source.request.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.model.HostLocationUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi

@Immutable
data class SyncRequestState(
    val request: SyncRequestUi? = null,
    val location: HostLocationUi = HostLocationUi.AppStorage,
    val folder: HostLocationUi.Folder? = null,
    val folderHasFiles: Boolean = false,
    val disk: DiskUi? = null,
    val preferences: SourcePreferencesUi = SourcePreferencesUi(),
    val isAnswering: Boolean = false,
) {
    val isGone: Boolean get() = request == null

    val isFolderSelected: Boolean get() = location is HostLocationUi.Folder

    val canAnswer: Boolean get() = request != null && !isAnswering

    @Immutable
    data class DiskUi(
        val totalBytes: Long,
        val freeBytes: Long,
    ) {
        val usedFraction: Float
            get() = if (totalBytes > 0) 1f - freeBytes.toFloat() / totalBytes else 0f

        fun fractionOf(bytes: Long): Float =
            if (totalBytes > 0) bytes.toFloat() / totalBytes else 0f
    }

    companion object {
        const val StepCount = 3

        val Sample = SyncRequestState(
            request = SyncRequestUi(
                sourceId = "3f2a",
                deviceName = "MacBook-Pro",
                label = "/Projects",
                mode = SourceModeUi.Sync,
                fingerprint = "4c81 aa07",
                files = 12,
                bytes = 480L * 1024 * 1024,
            ),
            disk = DiskUi(totalBytes = 128_000_000_000, freeBytes = 38_000_000_000),
            preferences = SourcePreferencesUi.build(SourceModeUi.Sync, SourceRoleUi.Follower),
        )
    }
}
