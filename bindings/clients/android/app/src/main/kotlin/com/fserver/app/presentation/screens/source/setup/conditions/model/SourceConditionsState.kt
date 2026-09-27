package com.fserver.app.presentation.screens.source.setup.conditions.model

import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi

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
    val preferences: SourcePreferencesUi = SourcePreferencesUi(),
    /** What the access step's scan found, for the backlog and size-limit hints. */
    val sourceFiles: Int? = null,
    val sourceBytes: Long? = null,
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
}
