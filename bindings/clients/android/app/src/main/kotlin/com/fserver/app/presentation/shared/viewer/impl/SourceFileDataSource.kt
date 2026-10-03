package com.fserver.app.presentation.shared.viewer.impl

import com.fserver.app.presentation.shared.browser.model.FileKey
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.fserver.core.files.FilesController
import com.fserver.core.files.access.SourceFileReader
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * A source's file for the player, read through `:core` - so an encrypted one plays as well as a
 * plain one. Addressed by [uriOf]; ExoPlayer calls in on its loader thread, which may block.
 */
@UnstableApi
internal class SourceFileDataSource(private val files: FilesController) : BaseDataSource(false) {
    private var reader: SourceFileReader? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var scratch = ByteArray(0)

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val key = keyOf(dataSpec.uri)
        val opened = runBlocking { files.file(key.sourceId, key.fileId)?.openReader() }
            ?: throw FileNotFoundException("${dataSpec.uri} is not held here")
        reader = opened
        uri = dataSpec.uri

        val size = runBlocking { opened.size() }
        if (dataSpec.position > size) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        position = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else size - position

        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT

        val wanted = minOf(length.toLong(), remaining).toInt()
        if (scratch.size < wanted) scratch = ByteArray(wanted)
        val read = runBlocking { checkNotNull(reader).read(position, scratch, wanted) }
        if (read < 0) return C.RESULT_END_OF_INPUT

        scratch.copyInto(buffer, offset, 0, read)
        position += read
        remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        reader?.let {
            reader = null
            it.close()
            transferEnded()
        }
    }

    class Factory(private val files: FilesController) : DataSource.Factory {
        override fun createDataSource(): DataSource = SourceFileDataSource(files)
    }

    companion object {
        private const val Scheme = "fserver-source"

        fun uriOf(key: FileKey): Uri =
            Uri.Builder().scheme(Scheme).appendPath(key.sourceId).appendPath(key.fileId).build()

        private fun keyOf(uri: Uri): FileKey {
            val segments = uri.pathSegments
            require(uri.scheme == Scheme && segments.size == 2) { "Not a source file: $uri" }
            return FileKey(fileId = segments[1], sourceId = segments[0])
        }
    }
}
