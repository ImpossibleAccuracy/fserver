package com.fserver.app.presentation.shared.viewer.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.getExtra
import coil3.request.Options
import coil3.size.Size
import coil3.toBitmap
import coil3.video.MediaDataSourceFetcher.MediaSourceMetadata
import com.fserver.app.data.preview.EvictionPreviews
import com.fserver.app.data.preview.VersionedImages
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.core.files.FilesController
import com.fserver.core.files.access.SourceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import java.io.FileNotFoundException

/**
 * Loads [FileImage]. A source's bytes come through `:core`, so an encrypted file decodes like any
 * other; a plain one on disk goes to Coil's own fetchers by path, which is faster. An audio file
 * gives the picture in its tags.
 *
 * A tile ([FileImage.acceptCache]) is decoded at most [ThumbnailSide] and kept in Coil's disk
 * cache; with [keepPreview] it is kept in [EvictionPreviews] too, which is all there is once the
 * bytes are evicted. Both drop a picture of an older version when asked for it.
 */
internal class FileImageFetcher(
    private val data: FileImage,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val files: FilesController,
    private val previews: EvictionPreviews,
) : Fetcher {
    private val thumbnails = imageLoader.diskCache?.let(::VersionedImages)
    private val key = data.cacheKey

    override suspend fun fetch(): FetchResult {
        val key = data.file
        val file =
            if (key != null && data.locator != null) files.file(key.sourceId, key.fileId)
            else null

        val present = data.locator != null && (key == null || file != null)

        if (!present) return kept()
            ?: throw FileNotFoundException("No bytes or preview of ${data.label}")

        if (!data.acceptCache) return whole(file)
        return thumbnail(file)
    }

    private suspend fun thumbnail(file: SourceFile?): FetchResult = withContext(Dispatchers.IO) {
        val keepPreview = options.getExtra(KeepPreview)

        thumbnails?.open(key, data.version)?.let { snapshot ->
            if (keepPreview) previews.images.copy(snapshot, thumbnails.diskCache, key, data.version)
            return@withContext thumbnails.result(snapshot, key)
        }

        val bitmap = decodeThumbnail(file)
        thumbnails?.save(key, data.version, bitmap)
        if (keepPreview) previews.images.save(key, data.version, bitmap)

        ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    /** With the bytes gone: the eviction preview, else a tile still cached from before. */
    private fun kept(): FetchResult? {
        previews.images.open(key, data.version)?.let { return previews.images.result(it, key) }
        return thumbnails?.let { cache ->
            cache.open(key, data.version)?.let { cache.result(it, key) }
        }
    }

    private suspend fun whole(file: SourceFile?): FetchResult = when {
        data.kind == FileKindUi.Audio -> {
            val (artwork, sampled) = readArtwork(file, MaxArtworkSide)
            ImageFetchResult(
                image = artwork.asImage(),
                isSampled = sampled,
                dataSource = DataSource.DISK
            )
        }

        file == null || file.openDescriptor()?.use { true } == true -> byLocator()
        else -> throughCore(file)
    }

    /** Decoded here rather than by the pipeline, so the picture can be stored as well as shown. */
    private suspend fun decodeThumbnail(file: SourceFile?): Bitmap {
        if (data.kind == FileKindUi.Audio) return readArtwork(file, ThumbnailSide).first

        val bounded = options.copy(size = Size(ThumbnailSide, ThumbnailSide))
        val image = when (val result = whole(file)) {
            is ImageFetchResult -> result.image
            is SourceFetchResult -> {
                val (decoder, _) = checkNotNull(
                    imageLoader.components.newDecoder(
                        result,
                        bounded,
                        imageLoader
                    )
                ) {
                    "Nothing decodes ${data.label}"
                }
                checkNotNull(decoder.decode()) { "Undecodable ${data.label}" }.image
            }
        }

        val bitmap = image.toBitmap()
        // A hardware bitmap does not compress; there are none before API 26.
        val hardware =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE
        return if (hardware) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    }

    private suspend fun byLocator(): FetchResult {
        val uri = locatorUri(checkNotNull(data.locator) { "No locator for ${data.label}" })
        val mapped = imageLoader.components.map(uri, options)
        val (fetcher, _) = checkNotNull(
            imageLoader.components.newFetcher(
                mapped,
                options,
                imageLoader
            )
        ) {
            "Nothing fetches $uri"
        }
        return checkNotNull(fetcher.fetch()) { "Nothing fetched from $uri" }
    }

    /** Streamed for image decoders, seekable for the video one through the metadata. */
    private suspend fun throughCore(file: SourceFile): FetchResult {
        val media = ReaderMediaDataSource(file.openReader())
        return SourceFetchResult(
            source = ImageSource(
                source = MediaDataSourceSource(media).buffer(),
                fileSystem = options.fileSystem,
                metadata = MediaSourceMetadata(media),
            ),
            mimeType = data.mimeType,
            dataSource = DataSource.DISK,
        )
    }

    /** A source's file through `:core`; a scanned one by path. */
    private suspend fun readArtwork(file: SourceFile?, maxSide: Int): Pair<Bitmap, Boolean> {
        val retriever = MediaMetadataRetriever()
        val picture = try {
            if (file != null) {
                ReaderMediaDataSource(file.openReader()).use { media ->
                    retriever.setDataSource(media)
                    retriever.embeddedPicture
                }
            } else {
                retriever.setDataSource(options.context, locatorUri(checkNotNull(data.locator)))
                retriever.embeddedPicture
            }
        } finally {
            retriever.release()
        }
        checkNotNull(picture) { "No artwork in ${data.label}" }

        val sample = sampleSize(picture, maxSide)
        val decoded = BitmapFactory.decodeByteArray(
            picture,
            0,
            picture.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
        checkNotNull(decoded) { "Undecodable artwork in ${data.label}" }

        return decoded.withoutLetterbox() to (sample > 1)
    }

    class Factory(
        private val files: FilesController,
        private val previews: EvictionPreviews,
    ) : Fetcher.Factory<FileImage> {
        override fun create(data: FileImage, options: Options, imageLoader: ImageLoader): Fetcher =
            FileImageFetcher(data, options, imageLoader, files, previews)
    }
}

/** Enough for a full-width tile on a phone; the viewer decodes the file whole. */
private const val ThumbnailSide = 512

/** Some files embed a print-sized scan; [maxSide] is all a tile or the screen shows. */
private fun sampleSize(picture: ByteArray, maxSide: Int): Int {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
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

    val pillar =
        minOf(darkRun(fromStart = true, rows = false), darkRun(fromStart = false, rows = false))
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

        val dark = pixels.count {
            maxOf(
                it shr 16 and 0xFF,
                it shr 8 and 0xFF,
                it and 0xFF
            ) <= DarkChannel
        }
        if (dark < length * DarkShare) return step
    }
    return lines / 2
}

private const val MaxArtworkSide = 1024

/** Channel value up to which JPEG noise on a black bar still counts as black. */
private const val DarkChannel = 24

/** Share of a line that must be black for it to count as part of a bar. */
private const val DarkShare = 0.98f
