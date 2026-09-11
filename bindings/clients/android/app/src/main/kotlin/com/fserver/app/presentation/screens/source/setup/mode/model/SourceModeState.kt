package com.fserver.app.presentation.screens.source.setup.mode.model

import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.model.modes
import com.fserver.common.model.FileSize

/**
 * One mode per source, picked once access is in hand.
 *
 * The list is ordered from the safest to the most irreversible, and the recommended one is
 * selected up front — the difference between the modes is one sentence about what happens to
 * the original.
 */
data class SourceModeState(
    val kind: SourceKindUi,
    val selected: SourceModeUi? = null,
    val accessType: AccessType? = null,
) {
    val modes: List<SourceModeUi> = kind.modes

    val canContinue: Boolean get() = selected != null

    sealed interface AccessType {
        data class Partial(
            val grantedItemCount: Int,
        ) : AccessType

        data class Full(
            val label: String,
            val files: Int,
            val size: FileSize,
        ) : AccessType
    }
}
