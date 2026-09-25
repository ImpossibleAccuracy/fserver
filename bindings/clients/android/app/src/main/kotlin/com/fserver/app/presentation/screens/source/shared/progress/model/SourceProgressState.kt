package com.fserver.app.presentation.screens.source.shared.progress.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.model.SourceRoleUi

@Immutable
data class SourceProgressState(
    val role: SourceRoleUi = SourceRoleUi.Initiator,
    val phase: Phase = Phase.Waiting,
    val peerName: String = "",
    val sourceLabel: String = "",
    val progress: Float? = null,
    val actionsPlanned: Int = 0,
    val actionsDone: Int = 0,
    val isCounted: Boolean = false,
    val isPlanned: Boolean = false,
    val filesDone: Int = 0,
    val filesTotal: Int = 0,
    val reason: String? = null,
) {
    val isTransferring: Boolean
        get() = filesTotal > 0 && filesDone < filesTotal

    val showsPassDetail: Boolean
        get() = isCounted && !isTransferring

    val displayedProgress: Float?
        get() = when {
            showsPassDetail -> progress
            filesTotal > 0 -> filesDone.toFloat() / filesTotal
            else -> null
        }

    enum class Phase {
        Waiting,

        Syncing,

        Refused,
    }
}
