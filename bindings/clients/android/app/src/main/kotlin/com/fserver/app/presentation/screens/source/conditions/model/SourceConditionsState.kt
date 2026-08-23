package com.fserver.app.presentation.screens.source.conditions.model

import com.fserver.app.presentation.composable.model.EvictCriterionUi
import com.fserver.app.presentation.composable.model.HostRightsUi
import com.fserver.app.presentation.composable.model.SourceKindUi
import com.fserver.app.presentation.composable.model.SourceModeUi
import com.fserver.app.presentation.composable.model.UploadScopeUi

/**
 * Everything a mode still needs to know before it can be turned on, plus the work that runs
 * between the last answer and the first byte.
 *
 * One state for all four modes: they ask different questions but share the shape — a form,
 * then a measurable step, then a summary. Only the fields the current mode reads are used.
 */
data class SourceConditionsState(
    val kind: SourceKindUi,
    val mode: SourceModeUi,
    val phase: Phase = Phase.Form,
    val targetName: String = "",
    val sourceLabel: String = "",
    /** Auto-upload: whether the backlog comes along, and what it costs. */
    val uploadScope: UploadScopeUi = UploadScopeUi.New,
    val backlogLabel: String = "",
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    /** Offload: which files leave first, and what is exempt whatever the rule says. */
    val criterion: EvictCriterionUi = EvictCriterionUi.OlderThanDays,
    val olderThanDays: Int = DefaultDays,
    val keepPinned: Boolean = true,
    /** Host: what trusted devices may do with the folder. */
    val hostRights: HostRightsUi = HostRightsUi.ReadOnly,
    val progress: Float = 0f,
    val progressDetail: String = "",
) {
    enum class Phase {
        /**
         * Offload only, and only the first time: it deletes originals, so "it disappears from
         * the gallery" has to be said before the switch, not after.
         */
        Explainer,

        Form,

        /** Checking what the target already has, or counting what the rule would evict. */
        Preparing,
    }

    companion object {
        const val DefaultDays = 60
        const val DaysStep = 15
        const val MinDays = 15
        const val MaxDays = 365
    }
}
