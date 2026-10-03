package com.fserver.app.presentation.shared.viewer.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException

/**
 * A PDF as a column of pages, each rendered at the screen's width once it scrolls into view.
 * The platform renderer needs a seekable descriptor; an encrypted source on API 24-25 only has a
 * pipe, so it lands on [ViewerFailure] and goes to another app.
 */
@Composable
internal fun PdfViewer(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
) {
    val context = LocalContext.current
    var state by remember(file.locator) { mutableStateOf<PdfState>(PdfState.Loading) }

    LaunchedEffect(file.locator) {
        val document = try {
            withContext(Dispatchers.IO) { PdfDocument.open(context, file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot open %s", file.name)
            state = PdfState.Failed
            return@LaunchedEffect
        }

        try {
            state = PdfState.Open(document)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { document.close() }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val current = state) {
            PdfState.Loading -> CircularProgressIndicator(color = Color.White)
            PdfState.Failed -> ViewerFailure(modifier = Modifier.fillMaxSize(), file = file)
            is PdfState.Open -> PdfPages(modifier = Modifier.fillMaxSize(), document = current.document)
        }
    }
}

@Composable
private fun PdfPages(
    modifier: Modifier = Modifier,
    document: PdfDocument,
) {
    BoxWithConstraints(modifier = modifier) {
        val width = constraints.maxWidth

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = documentPadding(),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            items(count = document.pageCount) { index ->
                PdfPage(document = document, index = index, width = width)
            }
        }
    }
}

@Composable
private fun PdfPage(
    modifier: Modifier = Modifier,
    document: PdfDocument,
    index: Int,
    width: Int,
) {
    val page by produceState<ImageBitmap?>(initialValue = null, document, index, width) {
        value = try {
            document.render(index, width).asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot render page %d", index)
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(document.ratios[index])
            .background(Color.White),
    ) {
        page?.let {
            Image(modifier = Modifier.fillMaxSize(), bitmap = it, contentDescription = null)
        }
    }
}

private sealed interface PdfState {
    data object Loading : PdfState
    data object Failed : PdfState
    class Open(val document: PdfDocument) : PdfState
}

/** [PdfRenderer] opens one page at a time and is not thread-safe, so every call takes [lock]. */
private class PdfDocument(private val renderer: PdfRenderer) {
    private val lock = Mutex()

    val pageCount: Int = renderer.pageCount

    /** Width over height of every page, read up front so the column does not jump as pages render. */
    val ratios: List<Float> = List(pageCount) { index ->
        val page = renderer.openPage(index)
        page.use { page ->
            page.width.toFloat() / page.height.coerceAtLeast(1)
        }
    }

    suspend fun render(index: Int, width: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            val page = renderer.openPage(index)
            page.use { page ->
                val height = (width / ratios[index]).toInt().coerceAtLeast(1)
                val bitmap = createBitmap(width, height)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    suspend fun close() = lock.withLock { renderer.close() }

    companion object {
        /** The renderer owns the descriptor from here on and closes it with itself. */
        fun open(context: Context, file: FileBrowserUi.File): PdfDocument {
            val uri = context.readableUri(file) ?: throw FileNotFoundException(file.name)
            val descriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw FileNotFoundException(uri.toString())

            val renderer = try {
                PdfRenderer(descriptor)
            } catch (e: Exception) {
                descriptor.close()
                throw e
            }
            return try {
                PdfDocument(renderer)
            } catch (e: Exception) {
                renderer.close()
                throw e
            }
        }
    }
}
