package com.fserver.app.presentation.screens.source.access.model

import com.fserver.app.presentation.composable.model.SourceAccessUi

sealed interface SourceAccessIntent {
    /** The explainer was accepted — this is where the system dialog is raised. */
    data object AccessRequested : SourceAccessIntent

    /**
     * What the system came back with. Kept separate from [AccessRequested] because the answer
     * arrives from a permission launcher (or, for the whole-device branch, from a resume check),
     * not from the tap that started it.
     */
    data class AccessAnswered(val access: SourceAccessUi?) : SourceAccessIntent

    data object ScanCancelled : SourceAccessIntent
}
