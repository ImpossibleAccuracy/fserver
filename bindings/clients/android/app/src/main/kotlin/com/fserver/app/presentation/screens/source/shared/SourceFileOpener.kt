package com.fserver.app.presentation.screens.source.shared

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.fserver.app.R
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import timber.log.Timber
import java.io.File

/**
 * Opens a previewed file in whatever app the system has for it.
 *
 * A scan reports two kinds of address: a content URI, which is already shareable, and an absolute
 * path, which no other app may read. The second one is handed out through the app's `FileProvider`
 * with a one-shot read grant, so the viewer sees the file and nothing else.
 */
@Composable
fun rememberSourceFileOpener(): (SourcePreviewUi.File) -> Unit {
    val context = LocalContext.current

    return remember(context) { { file -> context.openInSystemViewer(file) } }
}

private fun Context.openInSystemViewer(file: SourcePreviewUi.File) {
    val uri = shareableUri(file.locator)
    if (uri == null) {
        Toast.makeText(this, R.string.source_preview_open_failed, Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeTypeOf(file.name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    try {
        startActivity(Intent.createChooser(intent, null))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No activity opens %s", file.name)
        Toast.makeText(this, R.string.source_preview_open_failed, Toast.LENGTH_SHORT).show()
    }
}

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
