package com.fserver.app.presentation.screens.source.setup.shared.model

import androidx.compose.runtime.Immutable
import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation

/** What the access step's scan found, for the screens after it. */
@Immutable
data class PickedSourceUi(
    /** Name of source (directory name or generic label) */
    val label: String,
    /** Found files amount */
    val files: Int,
    /** Found files size */
    val bytes: FileSize,
    /** What the engine registers as the source, once the flow is confirmed. */
    val location: SourceLocation.Selectable,
)
