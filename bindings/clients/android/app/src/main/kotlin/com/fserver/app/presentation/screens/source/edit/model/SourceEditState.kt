package com.fserver.app.presentation.screens.source.edit.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi

@Immutable
data class SourceEditState(
    val isLoading: Boolean = true,
    val label: String = "",
    val peerName: String = "",
    val role: SourceRoleUi = SourceRoleUi.Initiator,
    val preferences: SourcePreferencesUi = SourcePreferencesUi(),
    val sourceFiles: Int? = null,
    val sourceBytes: Long? = null,
    val isChanged: Boolean = false,
    val isSaving: Boolean = false,
) {
    val canSave: Boolean
        get() = isChanged && !isSaving

    companion object {
        val Sample = SourceEditState(
            isLoading = false,
            label = "Documents",
            peerName = "Laptop",
            role = SourceRoleUi.Initiator,
            preferences = SourcePreferencesUi.build(SourceModeUi.Sync, SourceRoleUi.Initiator),
            sourceFiles = 1208,
            sourceBytes = 2_400_000_000,
            isChanged = true,
        )

        val SampleFollower = Sample.copy(
            role = SourceRoleUi.Follower,
            preferences = SourcePreferencesUi.build(SourceModeUi.Sync, SourceRoleUi.Follower),
            isChanged = false,
        )
    }
}
