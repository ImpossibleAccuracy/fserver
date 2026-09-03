package com.fserver.app.presentation.screens.request.shared.model

import com.fserver.core.files.SourceLocation

/**
 * Where the files arriving for an accepted source are written.
 *
 * App-private storage is the default because it needs no grant and no folder of the user's is
 * touched. A folder is the opt-out, and only one the user picked in the system dialog.
 */
sealed interface HostLocationUi {
    data object AppStorage : HostLocationUi

    data class Folder(val uri: String, val label: String) : HostLocationUi
}

/**
 * What the engine registers. App-private storage is bucketed by source id, so two hosted sources
 * never write over each other.
 */
fun HostLocationUi.toLocation(sourceId: String): SourceLocation.Hostable = when (this) {
    HostLocationUi.AppStorage -> SourceLocation.Internal(bucket = sourceId)
    is HostLocationUi.Folder -> SourceLocation.Tree(path = uri)
}
