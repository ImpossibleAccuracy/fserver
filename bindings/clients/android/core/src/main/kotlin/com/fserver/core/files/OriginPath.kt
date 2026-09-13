package com.fserver.core.files

import com.fserver.common.utils.SourcePaths

/** What [SourceLocation.Media] reports as its path: it addresses a collection, not a directory. */
private const val MediaPath = "Media"

/**
 * The directory this source points at, as a person reads it - what
 * [com.fserver.core.sync.model.SourceEntry.originPath] is stamped with when a source is registered.
 *
 * Derived here and nowhere else: the peer stores the value verbatim, so two devices deriving it
 * differently would describe the same source two ways.
 */
internal fun SourceLocation.Persistable.toOriginPath(): String = when (this) {
    is SourceLocation.Tree -> SourcePaths.readable(path)
    is SourceLocation.Directory -> SourcePaths.readable(path)
    is SourceLocation.Internal -> bucket
    SourceLocation.Media -> MediaPath
}
