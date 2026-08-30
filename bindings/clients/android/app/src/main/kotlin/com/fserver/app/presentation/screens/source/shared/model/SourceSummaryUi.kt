package com.fserver.app.presentation.screens.source.shared.model

import com.fserver.app.presentation.screens.source.shared.composable.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.composable.SourceModeUi
import kotlinx.serialization.Serializable

/**
 * What the flow committed, in the shape the summary screen reads it.
 *
 * It travels inside the done destination rather than in the flow's ViewModel: by the time that
 * screen is shown every screen of the flow — including the one its store is scoped to — has
 * been dropped from the back stack, so the store is already gone.
 */
@Serializable
data class SourceSummaryUi(
    val kind: SourceKindUi,
    val mode: SourceModeUi,
    val sourceLabel: String,
    val files: Int,
    val bytes: Long,
    val olderThanDays: Int,
)
