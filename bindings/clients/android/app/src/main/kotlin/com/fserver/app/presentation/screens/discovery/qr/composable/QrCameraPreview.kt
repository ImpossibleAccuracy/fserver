package com.fserver.app.presentation.screens.discovery.qr.composable

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkPlaceholderBox
import androidx.compose.ui.res.stringResource
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * The back camera, bound to this composable's lifecycle, with every frame handed to a QR decoder.
 *
 * [onCode] fires on the main thread for each successful decode, repeatedly while the code stays in
 * frame - deduplicating is the caller's business. [isActive] pauses decoding without tearing the
 * camera down, so a connection attempt does not fire a second one behind it.
 */
@Composable
fun QrCameraPreview(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    onCode: (String) -> Unit,
) {
    if (LocalInspectionMode.current) {
        DkPlaceholderBox(
            modifier = modifier,
            label = stringResource(R.string.qr_camera_placeholder),
            dashedBorder = false,
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentIsActive by rememberUpdatedState(isActive)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner, previewView, analysisExecutor) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analyzer = QrCodeAnalyzer { code ->
            mainExecutor.execute { if (currentIsActive) currentOnCode(code) }
        }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = try {
                future.get()
            } catch (e: Exception) {
                Timber.w(e, "Camera provider unavailable")
                return@addListener
            }

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor, analyzer)

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (e: Exception) {
                // No back camera, or another app holds it: the screen still offers a typed address.
                Timber.w(e, "Cannot bind camera")
            }
        }, mainExecutor)

        onDispose {
            if (future.isDone) {
                runCatching { future.get().unbindAll() }
            } else {
                future.cancel(false)
            }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )
}

/**
 * Decodes QR codes out of the luminance plane of each frame.
 *
 * Only the Y plane is read: zxing works off brightness alone, so the colour planes are never
 * touched and no bitmap is allocated per frame.
 */
private class QrCodeAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    override fun analyze(image: ImageProxy) {
        try {
            decode(image)?.let(onCode)
        } catch (e: Exception) {
            Timber.w(e, "Frame dropped")
        } finally {
            image.close()
        }
    }

    private fun decode(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.rewind()
        buffer.get(data)

        val source = PlanarYUVLuminanceSource(
            data,
            plane.rowStride,
            image.height,
            0,
            0,
            minOf(image.width, plane.rowStride),
            image.height,
            false,
        )

        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }
}
