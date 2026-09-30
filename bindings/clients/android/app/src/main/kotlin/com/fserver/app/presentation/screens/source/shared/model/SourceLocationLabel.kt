package com.fserver.app.presentation.screens.source.shared.model

import com.fserver.common.utils.SourcePaths
import com.fserver.core.files.SourceLocation

/**
 * A location as a person reads it, or null when there is nothing readable to show.
 *
 * A tree grant is addressed by a `content://` uri - an opaque handle, not a path - so it is
 * decoded back to the document id the system picker displayed rather than printed raw. The
 * encoded form is never put in front of the user.
 */
fun SourceLocation.readablePath(): String? = when (this) {
    is SourceLocation.Tree -> SourcePaths.readable(path)
    is SourceLocation.Directory -> SourcePaths.readable(path)
    is SourceLocation.Downloads -> "/Download/$directory"
    is SourceLocation.Internal,
    is SourceLocation.Root,
    SourceLocation.Media -> null
}
