package com.fserver.app.presentation.designkit

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import com.fserver.app.presentation.theme.FServerTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * A QR code for [payload], drawn as flat modules rather than a bitmap so it stays sharp at any size.
 *
 * Deliberately dark-on-white in both themes: a scanner reads contrast, and an inverted code is not
 * reliably decodable. The quiet zone around it is part of the format, not padding to taste.
 */
@Composable
fun DkQrCode(
    modifier: Modifier = Modifier,
    payload: String,
) {
    val matrix = remember(payload) { encode(payload) } ?: return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.White)
            .padding(DkSpacing.md),
    ) {
        val module = size.minDimension / matrix.width
        val moduleSize = Size(module, module)

        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (!matrix.get(x, y)) continue

                drawRect(
                    color = Color.Black,
                    topLeft = Offset(x * module, y * module),
                    size = moduleSize,
                )
            }
        }
    }
}

/**
 * [payload] as a dark-on-white bitmap with a quiet zone, for handing the code outside the app.
 * Null when it cannot be encoded. Blocking: call off the main thread.
 */
fun dkQrBitmap(payload: String, modulePx: Int = 12): Bitmap? {
    val matrix = encode(payload) ?: return null
    val quiet = QUIET_ZONE_MODULES * modulePx
    val side = matrix.width * modulePx + quiet * 2

    val bitmap = createBitmap(side, side)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = Paint().apply { color = android.graphics.Color.BLACK }
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            if (!matrix.get(x, y)) continue

            val left = (quiet + x * modulePx).toFloat()
            val top = (quiet + y * modulePx).toFloat()
            canvas.drawRect(left, top, left + modulePx, top + modulePx, paint)
        }
    }
    return bitmap
}

/** Null when the payload cannot be encoded at all - too long for the format, in practice. */
private fun encode(payload: String): BitMatrix? = try {
    QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        SIZE,
        SIZE,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            // The composable draws its own quiet zone, so the matrix carries none.
            EncodeHintType.MARGIN to 0,
        ),
    )
} catch (_: WriterException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

/** Requested matrix size in modules; the encoder rounds it up to the version it needs. */
private const val SIZE = 256

/** The format's required margin, in modules. */
private const val QUIET_ZONE_MODULES = 4

@Preview(showBackground = true)
@Composable
private fun DkQrCodePreview() {
    FServerTheme {
        DkQrCode(
            modifier = Modifier.fillMaxSize(),
            payload = """{"ip":"192.168.1.42","port":29470}""",
        )
    }
}
