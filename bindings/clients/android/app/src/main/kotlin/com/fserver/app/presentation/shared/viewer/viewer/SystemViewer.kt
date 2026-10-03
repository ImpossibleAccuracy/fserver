package com.fserver.app.presentation.shared.viewer.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.fserver.app.R
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.domain.documents.DocumentIds
import com.fserver.app.domain.documents.OwnDocumentsAuthority
import timber.log.Timber
import java.io.File

/**
 * Hands [file] to another app with a one-shot read grant. A source's file goes out through the
 * app's own documents provider, which reads through `:core` - an encrypted one too. A scanned one
 * by path goes through `FileProvider`, a content URI as is.
 */
internal fun Context.openInSystemViewer(file: FileBrowserUi.File) {
    val uri = readableUri(file)
    if (uri == null) {
        Toast.makeText(this, R.string.file_viewer_open_failed, Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeTypeOf(file.name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    try {
        startActivity(Intent.createChooser(intent, null))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No activity opens %s", file.name)
        Toast.makeText(this, R.string.file_viewer_open_failed, Toast.LENGTH_SHORT).show()
    }
}

/** [file] as a content URI any reader can open, this app's own viewers included. */
internal fun Context.readableUri(file: FileBrowserUi.File): Uri? =
    file.key?.let { DocumentsContract.buildDocumentUri(OwnDocumentsAuthority, DocumentIds.of(it.sourceId, file.path)) }
        ?: file.locator?.let(::shareableUri)

private fun Context.shareableUri(locator: String): Uri? {
    if (!locator.startsWith('/')) return locator.toUri()

    return runCatching {
        FileProvider.getUriForFile(this, "$packageName.fileprovider", File(locator))
    }.onFailure { Timber.w(it, "Cannot share %s", locator) }.getOrNull()
}

private fun mimeTypeOf(name: String): String {
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: FallbackMimeType
}

private const val FallbackMimeType = "*/*"
