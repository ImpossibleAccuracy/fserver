package com.fserver.app.presentation.permission

import androidx.compose.runtime.Immutable

/**
 * The system call that clears one unmet requirement.
 */
@Immutable
sealed interface RequirementAction {

    /**
     * Ask for permissions in one launch, matching how [com.fserver.core.requirement.Requirement.RuntimePermission]
     * groups them: a partial grant is answered by checking again, not by asking for the rest.
     */
    data class RequestPermissions(val permissions: List<String>) : RequirementAction

    /**
     * Open a settings screen — [android.provider.Settings] action, started as an `Intent`.
     *
     * [scopedToApp] adds this app's `package:` uri, which the per-app screens need to land on this
     * app instead of on the device-wide list of every app that could ask.
     */
    data class OpenSettings(
        val intentAction: String,
        val scopedToApp: Boolean = false,
    ) : RequirementAction

    /** Hand the repair to Play services, which owns its own download / enable / update flow. */
    data object ResolvePlayServices : RequirementAction
}
