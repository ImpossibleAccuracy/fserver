package com.fserver.app.presentation.screens.files.editor.shared

import android.graphics.Bitmap
import android.os.Build
import com.fserver.app.presentation.composable.model.fileExtension
import java.io.ByteArrayOutputStream

/** Formats the editor can write back in place: the ones [Bitmap.compress] encodes. */
enum class EditableImageFormat(private val extensions: Set<String>) {
    Jpeg(setOf("jpg", "jpeg")),
    Png(setOf("png")),
    Webp(setOf("webp"));

    fun encode(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        check(bitmap.compress(compressFormat, Quality, out)) { "Could not encode as $this" }
        out.toByteArray()
    }

    private val compressFormat: Bitmap.CompressFormat
        get() = when (this) {
            Jpeg -> Bitmap.CompressFormat.JPEG
            Png -> Bitmap.CompressFormat.PNG
            Webp -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }

    companion object {
        private const val Quality = 95

        fun of(name: String): EditableImageFormat? =
            name.fileExtension.let { ext -> entries.firstOrNull { ext in it.extensions } }
    }
}
