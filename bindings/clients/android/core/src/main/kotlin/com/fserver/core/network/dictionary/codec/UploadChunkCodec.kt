package com.fserver.core.network.dictionary.codec

import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.ByteWriter
import java.nio.charset.StandardCharsets

/**
 * `UploadChunk` on the wire, by hand.
 *
 * Everything else here is JSON, which cannot carry bytes: kotlinx writes a `ByteArray` as an array
 * of numbers, so a byte of file costs about 3.6 on the wire. This is the one message that carries
 * the file itself, so it gets an encoding of its own.
 */
internal object UploadChunkCodec {
    /**
     * What tells a chunk from a JSON message, so it is written raw and first - a length in front
     * of it would be what the reader saw instead. No JSON document starts with these bytes.
     */
    private val Magic = "uploadChunk".toByteArray(StandardCharsets.UTF_8)

    fun isUploadChunk(bytes: ByteArray): Boolean {
        if (bytes.size < Magic.size) return false

        for (index in Magic.indices) {
            if (bytes[index] != Magic[index]) return false
        }

        return true
    }

    /**
     * What the codec writes around the bytes of a chunk carrying [key].
     *
     * Public so a sender can size a chunk to the frame it will travel in: the frame budget less
     * this is exactly how many bytes of file fit.
     */
    fun headerSize(key: UploadKey): Int =
        Magic.size + 1 + when (key) {
            is UploadKey.Source -> Int.SIZE_BYTES + key.sourceId.utf8Size + Int.SIZE_BYTES + key.fileId.utf8Size
            is UploadKey.OneShot -> Int.SIZE_BYTES + key.transferId.utf8Size + Int.SIZE_BYTES
        } + Long.SIZE_BYTES // offset

    fun encode(message: FileServerMessages.UploadChunk): ByteArray {
        val writer = ByteWriter(headerSize(message.key) + message.bytes.size).raw(Magic)

        when (val key = message.key) {
            is UploadKey.Source -> writer.u8(SourceTag).string(key.sourceId).string(key.fileId)
            is UploadKey.OneShot -> writer.u8(OneShotTag).string(key.transferId).i32(key.index)
        }

        return writer
            .i64(message.offset)
            // Last field: the frame carries its own length, so the bytes need no second one.
            .raw(message.bytes)
            .toByteArray()
    }

    fun decode(bytes: ByteArray): FileServerMessages.UploadChunk {
        val reader = ByteReader(bytes)

        // Read rather than skipped: decode is also reachable without the check that routed here.
        val magic = ByteArray(Magic.size) { reader.u8().toByte() }
        if (!magic.contentEquals(Magic)) {
            throw IllegalArgumentException("Not an UploadChunk message")
        }

        val key = when (val tag = reader.u8()) {
            SourceTag -> UploadKey.Source(sourceId = reader.string(), fileId = reader.string())
            OneShotTag -> UploadKey.OneShot(transferId = reader.string(), index = reader.i32())
            else -> throw IllegalArgumentException("Unknown upload key tag $tag")
        }

        return FileServerMessages.UploadChunk(
            key = key,
            offset = reader.i64(),
            bytes = reader.rest(),
        )
    }

    private const val SourceTag = 0
    private const val OneShotTag = 1

    private val String.utf8Size: Int get() = toByteArray(StandardCharsets.UTF_8).size
}
