package com.fserver.files.fs.impl

import android.webkit.MimeTypeMap
import com.fserver.common.exception.FileSystemException
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FsFile
import java.io.File

/**
 * A canonical path split into segments, with anything that walks out of the source refused.
 *
 * Every path handed to [FileSystem.createFile] came from a peer, so traversal is rejected here
 * rather than left to the backend underneath.
 */
internal fun segmentsOf(path: String): List<String> {
    val segments = path.split('/', '\\').filter { it.isNotEmpty() && it != "." }

    if (segments.isEmpty() || segments.any { it == ".." }) {
        throw FileSystemException.InvalidPath(path)
    }

    return segments
}

/** [name] as a single path segment, for [FsFile.rename]. Throws if it is anything more. */
internal fun nameOf(name: String): String =
    segmentsOf(name).singleOrNull() ?: throw FileSystemException.InvalidPath(name)

/** Mime type guessed from [name]'s extension, so a provider keeps the name it was given. */
internal fun mimeTypeOf(name: String): String =
    MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        ?: "application/octet-stream"

/**
 * True when [name] is an image, video or audio file - all a media source scans for. A media backend
 * refuses anything else, since a file its own scan never reports would be sent to it forever.
 */
internal fun isMediaName(name: String): Boolean =
    mimeTypeOf(name).substringBefore('/') in setOf("image", "video", "audio")

/** True when this file sits under [root]. Both sides must already be canonical. */
internal fun File.isUnder(root: File): Boolean = path.startsWith(root.path + File.separator)
