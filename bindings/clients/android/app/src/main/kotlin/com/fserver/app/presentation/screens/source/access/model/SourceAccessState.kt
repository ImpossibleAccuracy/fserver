package com.fserver.app.presentation.screens.source.access.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.SourceKindUi

/**
 * The access step of a branch: explain what is about to be asked for, ask the system, then
 * report what came back.
 *
 * Every branch has the same three phases even though the system side differs — photos get a
 * runtime dialog, a folder gets the SAF tree picker, the whole device gets no dialog at all and
 * a trip to system settings instead.
 */
@Immutable
data class SourceAccessState(
    val kind: SourceKindUi,
    val phase: Phase = Phase.Explaining,
    /** Only the folder branch scans before the mode question: the count has to be honest there. */
    val scanPath: String = "",
    val scannedFiles: Int = 0,
    val scannedBytes: Long = 0,
) {
    enum class Phase {
        Explaining,

        /** A folder was granted and is being walked, so the next screen can name a real size. */
        Scanning,

        /** Nothing was granted. Not an error the user made — see the copy. */
        Denied,
    }
}
