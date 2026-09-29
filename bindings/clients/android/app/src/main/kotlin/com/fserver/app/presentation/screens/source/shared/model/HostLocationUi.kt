package com.fserver.app.presentation.screens.source.shared.model

import com.fserver.core.files.SourceLocation
import kotlinx.serialization.Serializable

/**
 * Where the files arriving for an accepted source are written.
 *
 * App-private storage is the default because it needs no grant and no folder of the user's is
 * touched. A folder is the opt-out: one picked in the system dialog, or one browsed to on the
 * device itself under the all-files grant.
 */
@Serializable
sealed interface HostLocationUi {
    /** Something is already there, so what the peer sends lands next to it. */
    val hasFiles: Boolean

    @Serializable
    data object AppStorage : HostLocationUi {
        override val hasFiles: Boolean get() = false
    }

    @Serializable
    data class Folder(
        val uri: String,
        val label: String,
        override val hasFiles: Boolean = false,
    ) : HostLocationUi

    @Serializable
    data class Directory(
        val path: String,
        val label: String,
        override val hasFiles: Boolean = false,
    ) : HostLocationUi
}

/**
 * What the engine registers. App-private storage is bucketed by source id, so two hosted sources
 * never write over each other.
 */
fun HostLocationUi.toLocation(sourceId: String): SourceLocation.Hostable = when (this) {
    HostLocationUi.AppStorage -> SourceLocation.Internal(bucket = sourceId)
    is HostLocationUi.Folder -> SourceLocation.Tree(path = uri)
    is HostLocationUi.Directory -> SourceLocation.Directory(path = path)
}
