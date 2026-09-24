package com.fserver.app.presentation.shared.viewer.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options

/** Coil model for the picture embedded in an audio file's tags. */
internal data class AudioArtwork(val uri: Uri)

/**
 * Reads [AudioArtwork] out of the file's tags. A file without one fails the request, which is what
 * lets the caller fall back to an icon.
 *
 * Artwork ripped from a video is often a 4:3 frame with the black bars baked in; those are cut
 * off here, so a cropped tile shows the picture rather than the bars.
 */
internal class AudioArtworkFetcher(
    private val data: AudioArtwork,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val retriever = MediaMetadataRetriever()
        val picture = try {
            retriever.setDataSource(options.context, data.uri)
            retriever.embeddedPicture
        } finally {
            retriever.release()
        }
        checkNotNull(picture) { "No artwork in ${data.uri}" }

        val sample = sampleSize(picture)
        val decoded = BitmapFactory.decodeByteArray(
            picture,
            0,
            picture.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
        checkNotNull(decoded) { "Undecodable artwork in ${data.uri}" }

        return ImageFetchResult(
            image = decoded.withoutLetterbox().asImage(),
            isSampled = sample > 1,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<AudioArtwork> {
        override fun create(data: AudioArtwork, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioArtworkFetcher(data, options)
    }
}

/** Artwork is shown at tile or screen size at most, and some files embed a print-sized scan. */
private fun sampleSize(picture: ByteArray): Int {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MaxArtworkSide) sample *= 2
    return sample
}

/**
 * Cuts symmetric black bars off, on one axis only. A dark picture looks like one big bar, so a cut
 * never goes past 16:9 for a letterbox or 1:1 for a pillarbox — the shapes such bars pad out.
 */
private fun Bitmap.withoutLetterbox(): Bitmap {
    val bar = minOf(darkRun(fromStart = true, rows = true), darkRun(fromStart = false, rows = true))
        .coerceAtMost(((height - width * 9f / 16f) / 2).toInt().coerceAtLeast(0))
    if (bar > 0) return Bitmap.createBitmap(this, 0, bar, width, height - 2 * bar)

    val pillar = minOf(darkRun(fromStart = true, rows = false), darkRun(fromStart = false, rows = false))
        .coerceAtMost(((width - height) / 2).coerceAtLeast(0))
    if (pillar > 0) return Bitmap.createBitmap(this, pillar, 0, width - 2 * pillar, height)

    return this
}

/** How many edge rows (or columns) in a row are near black. */
private fun Bitmap.darkRun(fromStart: Boolean, rows: Boolean): Int {
    val lines = if (rows) height else width
    val length = if (rows) width else height
    val pixels = IntArray(length)

    for (step in 0 until lines / 2) {
        val line = if (fromStart) step else lines - 1 - step
        if (rows) {
            getPixels(pixels, 0, width, 0, line, width, 1)
        } else {
            getPixels(pixels, 0, 1, line, 0, 1, height)
        }

        val dark = pixels.count { maxOf(it shr 16 and 0xFF, it shr 8 and 0xFF, it and 0xFF) <= DarkChannel }
        if (dark < length * DarkShare) return step
    }
    return lines / 2
}

private const val MaxArtworkSide = 1024

/** Channel value up to which JPEG noise on a black bar still counts as black. */
private const val DarkChannel = 24

/** Share of a line that must be black for it to count as part of a bar. */
private const val DarkShare = 0.98f
