package com.fserver.app.presentation.shared.viewer.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.fserver.app.R
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.impl.locatorUri
import timber.log.Timber
import java.io.File

/**
 * Hands [file] to another app. An absolute path is readable by no one else, so it goes out through
 * the app's `FileProvider` with a one-shot read grant; a content URI is shareable as is.
 */
internal fun Context.openInSystemViewer(file: FileBrowserUi.File) {
    val uri = file.locator?.let(::shareableUri)
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

/** What this app itself reads [locator] through: no grant needed. */
internal fun FileBrowserUi.File.localUri(): Uri? = locator?.let(::locatorUri)

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
