package com.fserver.app.presentation.screens.source.shared.model

import com.fserver.core.files.SourceLocation
import java.net.URLDecoder
import kotlin.text.Charsets

/**
 * A location as a person reads it, or null when there is nothing readable to show.
 *
 * A tree grant is addressed by a `content://` uri - an opaque handle, not a path - so it is
 * decoded back to the document id the system picker displayed rather than printed raw. The
 * encoded form is never put in front of the user.
 */
fun SourceLocation.readablePath(): String? = when (this) {
    is SourceLocation.Tree -> path.asReadablePath()
    is SourceLocation.Directory -> path.asReadablePath()
    is SourceLocation.Internal,
    is SourceLocation.Root,
    SourceLocation.Media -> null
}

private const val ContentScheme = "content://"

private fun String.asReadablePath(): String {
    if (!startsWith(ContentScheme)) return this

    val encoded = substringAfterLast('/')
    val documentId = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }
        .getOrDefault(encoded)

    // "primary:DCIM/Projects" - the volume prefix means nothing outside the provider that issued it.
    val relative = documentId.substringAfter(':', "")

    return if (relative.isEmpty()) documentId else "/$relative"
}
