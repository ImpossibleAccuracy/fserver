package com.fserver.app.presentation.screens.source.setup.conditions.model

import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi

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
    val backlogLabel: String? = null,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val keepBoth: Boolean = false,
    val limitFiles: Boolean = false,
    val maxFiles: Int = DefaultMaxFiles,
    val limitSize: Boolean = false,
    val maxSizeGb: Int = DefaultMaxSizeGb,
    /** Offload: which files leave first, and what is exempt whatever the rule says. */
    val criterion: EvictCriterionUi = EvictCriterionUi.OlderThanDays,
    val olderThanDays: Int = DefaultDays,
    val keepPinned: Boolean = true,
    /** Host: what trusted devices may do with the folder. */
    val hostRights: HostRightsUi = HostRightsUi.ReadOnly,
    val progress: Float = 0f,
    val progressDetail: UiText? = null,
    /** Why registering the source failed, when it did. */
    val error: UiText? = null,
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

        /** The engine refused to register the source. Nothing was turned on. */
        Failed,
    }

    companion object {
        const val DefaultDays = 60
        const val DaysStep = 15
        const val MinDays = 15
        const val MaxDays = 365

        const val DefaultMaxFiles = 1000
        const val MaxFilesStep = 100
        const val MinMaxFiles = 100
        const val MaxMaxFiles = 100_000

        const val DefaultMaxSizeGb = 10
        const val MaxSizeStepGb = 1
        const val MinMaxSizeGb = 1
        const val MaxMaxSizeGb = 1024
    }
}
