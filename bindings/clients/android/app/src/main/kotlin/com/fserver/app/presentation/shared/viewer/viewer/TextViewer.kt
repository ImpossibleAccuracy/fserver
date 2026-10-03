package com.fserver.app.presentation.shared.viewer.viewer

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException

/**
 * A text file as UTF-8, monospaced, one lazy row per line. Only the first [MaxTextBytes] are read:
 * a log of hundreds of megabytes would otherwise end up in memory whole.
 */
@Composable
internal fun TextViewer(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
) {
    val context = LocalContext.current
    val text by produceState<LoadedText?>(initialValue = null, file.locator) {
        value = try {
            withContext(Dispatchers.IO) { context.readText(file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot read %s", file.name)
            LoadedText.Failed
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val loaded = text) {
            null -> CircularProgressIndicator(color = Color.White)
            LoadedText.Failed -> ViewerFailure(modifier = Modifier.fillMaxSize(), file = file)
            is LoadedText.Lines -> TextLines(modifier = Modifier.fillMaxSize(), text = loaded)
        }
    }
}

@Composable
private fun TextLines(
    modifier: Modifier = Modifier,
    text: LoadedText.Lines,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = documentPadding(),
    ) {
        items(text.lines) { line ->
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DkSpacing.screenPadding),
                text = line,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Color.White,
            )
        }
        if (text.truncated) {
            item {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.lg),
                    text = stringResource(R.string.file_viewer_text_truncated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Immutable
private sealed interface LoadedText {
    data class Lines(val lines: List<String>, val truncated: Boolean) : LoadedText
    data object Failed : LoadedText
}

private fun Context.readText(file: FileBrowserUi.File): LoadedText.Lines {
    val uri = readableUri(file) ?: throw FileNotFoundException(file.name)
    val input = contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())

    val bytes = input.use {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(ChunkSize)
        while (out.size() <= MaxTextBytes) {
            val read = it.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
    val truncated = bytes.size > MaxTextBytes
    val text = bytes.decodeToString(endIndex = minOf(bytes.size, MaxTextBytes))

    return LoadedText.Lines(lines = text.lines(), truncated = truncated)
}

private const val MaxTextBytes = 1024 * 1024

private const val ChunkSize = 64 * 1024
