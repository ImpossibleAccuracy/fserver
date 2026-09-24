package com.fserver.core.files

import com.fserver.common.utils.SourcePaths

/** What [SourceLocation.Media] reports as its path: it addresses a collection, not a directory. */
private const val MediaPath = "Media"

/**
 * The directory this source points at, as a person reads it - what
 * [com.fserver.core.sync.model.SourceEntry.originPath] is stamped with when a source is registered.
 *
 * Relative to its storage volume ("DCIM/Camera", not "/storage/emulated/0/DCIM/Camera"): the mount
 * point is local to this device and means nothing to the peer. [volumes] are the ones mounted here.
 *
 * Derived here and nowhere else: the peer stores the value verbatim, so two devices deriving it
 * differently would describe the same source two ways.
 */
internal fun SourceLocation.Persistable.toOriginPath(
    volumes: List<SourceLocation.Root.Volume>,
): String = when (this) {
    is SourceLocation.Tree -> SourcePaths.readable(path).trim('/')
    is SourceLocation.Directory -> path.relativeToVolume(volumes)
    is SourceLocation.Internal -> bucket
    SourceLocation.Media -> MediaPath
}

/** [this] with the longest volume mount point it sits under cut off; a volume root is its id. */
private fun String.relativeToVolume(volumes: List<SourceLocation.Root.Volume>): String {
    val volume = volumes
        .filter { this == it.path || startsWith(it.path.trimEnd('/') + "/") }
        .maxByOrNull { it.path.length }
        ?: return trim('/')

    return removePrefix(volume.path.trimEnd('/')).trim('/').ifEmpty { volume.id }
}
