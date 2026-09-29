package com.fserver.app.presentation.screens.source.request.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.model.HostLocationUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.shared.browser.FileBrowserNavigation
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.core.files.scan.DirectoryScanProgress

@Immutable
data class SyncRequestState(
    val request: SyncRequestUi? = null,
    val location: HostLocationUi = HostLocationUi.AppStorage,
    val folder: HostLocationUi.Folder? = null,
    val directory: HostLocationUi.Directory? = null,
    val picker: DirectoryPickerUi? = null,
    val disk: DiskUi? = null,
    val preferences: SourcePreferencesUi = SourcePreferencesUi(),
    val isAnswering: Boolean = false,
) {
    val isGone: Boolean get() = request == null

    val isFolderSelected: Boolean get() = location is HostLocationUi.Folder

    val isDirectorySelected: Boolean get() = location is HostLocationUi.Directory

    val canAnswer: Boolean get() = request != null && !isAnswering

    @Immutable
    data class DirectoryPickerUi(
        val phase: Phase = Phase.Scanning,
        val progress: DirectoryScanProgress? = null,
        val preview: FileBrowserUi? = null,
        val navigation: FileBrowserNavigation? = null,
    ) {
        val canConfirm: Boolean get() = navigation?.opened != null

        enum class Phase { Scanning, Browsing, Denied, Failed }
    }

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
