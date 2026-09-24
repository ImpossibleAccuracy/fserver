package com.fserver.app.presentation.screens.source.setup.access.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.common.model.FileSize
import com.fserver.core.files.scan.DirectoryScanProgress

@Immutable
data class SourceAccessState(
    val kind: SourceKindUi,
    val phase: Phase = Phase.Explaining,
    val access: SourceAccessUi = SourceAccessUi.Full,
    val label: String = "",
    val files: Int = 0,
    val bytes: FileSize = FileSize(0),
    val progress: DirectoryScanProgress? = null,
    val preview: FileBrowserUi? = null,
    val selection: FileBrowserSelection? = null,
) {
    /**
     * The whole-device branch narrows to one folder before it may go on. The second walk over
     * that folder carries no preview, which is what tells the two scans apart.
     */
    val isPickingDirectory: Boolean
        get() = phase == Phase.Scanned && kind == SourceKindUi.WholeDevice && preview != null

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
