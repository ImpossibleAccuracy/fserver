package com.fserver.app.presentation.shared.viewer.impl

import android.net.Uri
import androidx.core.net.toUri
import coil3.map.Mapper
import coil3.request.Options
import com.fserver.app.data.preview.EvictionPreviews
import com.fserver.app.presentation.composable.model.FileKindUi
import java.io.File

/**
 * The one Coil model for a file's picture, whatever state its bytes are in. [version] changes with
 * the content, so cached audio artwork does not outlive a retag.
 */
internal data class FileImage(
    val sourceId: String?,
    val fileId: String,
    val locator: String?,
    val kind: FileKindUi,
    val version: String? = null,
)

/**
 * Resolves [FileImage]: local bytes load as the file itself, or for audio the picture in its tags;
 * without them, the preview kept at eviction. Touches no disk — a missing preview fails the fetch.
 */
internal class FileImageMapper(private val previews: EvictionPreviews) : Mapper<FileImage, Any> {
    override fun map(data: FileImage, options: Options): Any? {
        val uri = data.locator?.let(::locatorUri)
        if (uri != null) return if (data.kind == FileKindUi.Audio) AudioArtwork(uri, data.version) else uri

        return data.sourceId?.let { previews.fileFor(it, data.fileId) }
    }
}

/** What this app itself reads [locator] through: no grant needed. */
internal fun locatorUri(locator: String): Uri =
    if (locator.startsWith('/')) Uri.fromFile(File(locator)) else locator.toUri()
