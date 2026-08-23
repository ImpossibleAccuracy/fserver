package com.fserver.app.presentation.model

import com.fserver.app.presentation.composable.model.SourceKindUi
import com.fserver.app.presentation.composable.model.SourceModeUi
import kotlinx.serialization.Serializable

/**
 * Why a target device is being picked. Both flows ask the same question — which connected
 * device gets these bytes — so they share one screen and differ only in what happens after.
 *
 * It travels inside [Destination.TargetDevice] rather than beside it: the destination is the
 * whole description of the screen, and a purpose passed separately is a second source of truth
 * the back stack cannot restore.
 */
@Serializable
sealed interface TargetPurpose {
    /** The picker made a selection and it is waiting for a recipient. */
    @Serializable
    data class SendFiles(val selectionId: String) : TargetPurpose

    /**
     * Screen 5d of the send flow: the step where the target finally gets a name. It sits
     * between the mode and that mode's conditions, for every branch.
     */
    @Serializable
    data class ConfigureSource(
        val kind: SourceKindUi,
        val mode: SourceModeUi,
    ) : TargetPurpose
}
