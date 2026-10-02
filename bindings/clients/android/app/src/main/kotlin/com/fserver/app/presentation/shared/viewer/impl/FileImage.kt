package com.fserver.app.presentation.shared.viewer.impl

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import coil3.Extras
import coil3.request.ImageRequest
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.common.model.FileSize
import java.io.File
import kotlin.time.Instant

/**
 * The one Coil model for a file's picture, whatever state its bytes are in - see
 * [FileImageFetcher]. [version] changes with the content - see [imageVersionOf] - and a cached
 * picture of another version is dropped.
 */
internal data class FileImage(
    /** Null for a file no source holds yet - a scan - which is read straight from [locator]. */
    val sourceId: String?,
    val fileId: String,
    /** Null once the bytes are gone: only the preview kept at eviction is left. */
    val locator: String?,
    val kind: FileKindUi,
    val mimeType: String?,
    val version: String? = null,
    /** A tile: a downsampled picture, cached on disk. Off for full screen, which decodes the file whole. */
    val acceptCache: Boolean = true,
) {
    /** One per file whatever its version, so a new one replaces the old. */
    val cacheKey: String
        get() = if (sourceId != null) "file:$sourceId/$fileId" else "path:$locator"
}

/** Same for every view of one file's content: tile, viewer, eviction preview. */
internal fun imageVersionOf(modifiedAt: Instant?, size: FileSize?): String =
    "${modifiedAt?.toEpochMilliseconds()}:${size?.bytes}"

/** Asks [FileImageFetcher] to keep what it decodes as the file's eviction preview. */
internal val KeepPreview = Extras.Key(default = false)

internal fun ImageRequest.Builder.keepPreview() = apply { extras[KeepPreview] = true }

/** What this app itself reads [locator] through: no grant needed. */
internal fun locatorUri(locator: String): Uri =
    if (locator.startsWith('/')) Uri.fromFile(File(locator)) else locator.toUri()

/** Guessed from [name]'s extension; null when it has none the platform knows. */
internal fun mimeTypeOf(name: String): String? =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
