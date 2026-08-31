package com.fserver.app.presentation.screens.source.access.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.model.PickedSourceUi
import com.fserver.app.presentation.screens.source.shared.model.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.core.files.scan.DirectoryScanProgress

@Immutable
data class SourceAccessState(
    val kind: SourceKindUi,
    val phase: Phase = Phase.Explaining,
    val access: SourceAccessUi = SourceAccessUi.Full,
    val scanned: PickedSourceUi? = null,
    val progress: DirectoryScanProgress? = null,
) {
    enum class Phase {
        Explaining,

        /** A folder was granted and is being walked, so the next screen can name a real size. */
        Scanning,

        /** The walk finished. What it found is on screen, and continuing is the user's move. */
        Scanned,

        /** Nothing was granted. Not an error the user made — see the copy. */
        Denied,
    }
}
