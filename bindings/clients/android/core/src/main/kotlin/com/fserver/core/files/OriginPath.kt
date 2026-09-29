package com.fserver.core.files

import com.fserver.common.utils.SourcePaths
import com.fserver.core.sync.metadata.PeerSourceMetadata

/**
 * The directory this source points at, as a person reads it - what
 * [PeerSourceMetadata.storagePath] reports. Null where there is no directory a person could find:
 * app-private storage and the media library.
 *
 * Relative to its storage volume ("DCIM/Camera", not "/storage/emulated/0/DCIM/Camera"): the mount
 * point is local to this device and means nothing to the peer. [volumes] are the ones mounted here.
 *
 * Derived here and nowhere else: the peer stores the value verbatim, so two devices deriving it
 * differently would describe the same source two ways.
 */
internal fun SourceLocation.Persistable.toOriginPath(
    volumes: List<SourceLocation.Root.Volume>,
): String? = when (this) {
    is SourceLocation.Tree -> SourcePaths.readable(path).trim('/')
    is SourceLocation.Directory -> path.relativeToVolume(volumes)
    is SourceLocation.Internal,
    SourceLocation.Media -> null
}

internal fun SourceLocation.Persistable.storageKind(): PeerSourceMetadata.StorageKind = when (this) {
    is SourceLocation.Tree,
    is SourceLocation.Directory -> PeerSourceMetadata.StorageKind.Folder
    is SourceLocation.Internal -> PeerSourceMetadata.StorageKind.AppStorage
    SourceLocation.Media -> PeerSourceMetadata.StorageKind.Media
}

/** [this] with the longest volume mount point it sits under cut off; a volume root is its id. */
private fun String.relativeToVolume(volumes: List<SourceLocation.Root.Volume>): String {
    val volume = volumes
        .filter { this == it.path || startsWith(it.path.trimEnd('/') + "/") }
        .maxByOrNull { it.path.length }
        ?: return trim('/')

    return removePrefix(volume.path.trimEnd('/')).trim('/').ifEmpty { volume.id }
}
