package com.fserver.app.presentation.shared.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.viewer.openInSystemViewer

/**
 * Opens a file for the user: images, video and audio full screen in the app, anything else in
 * whatever app the system has for it. Provided once at the root through [LocalFileOpener].
 */
fun interface FileOpener {
    fun open(file: FileBrowserUi.File)
}

val LocalFileOpener = staticCompositionLocalOf<FileOpener> {
    error("No FileOpener provided")
}

/** [onView] shows a file in the in-app viewer; it only ever gets what [FileViewerHost] can show. */
@Composable
fun rememberFileOpener(onView: (FileBrowserUi.File) -> Unit): FileOpener {
    val context = LocalContext.current
    val currentOnView by rememberUpdatedState(onView)

    return remember(context) {
        FileOpener { file ->
            if (file.opensInApp) currentOnView(file) else context.openInSystemViewer(file)
        }
    }
}

internal val FileBrowserUi.File.opensInApp: Boolean
    get() = locator != null && kind in InAppKinds

private val InAppKinds = setOf(FileKindUi.Image, FileKindUi.Video, FileKindUi.Audio)
