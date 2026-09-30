package com.fserver.core.network.dictionary.codec

import com.fserver.common.exception.NetworkException
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.UploadKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one message with a hand-written wire format, and the only one carrying file bytes.
 *
 * Everything it decodes came off the network, so a truncated or hostile frame has to come back as
 * a protocol error rather than as an index out of bounds somewhere downstream.
 */
class UploadChunkCodecTest {

    @Test
    fun `a chunk survives the round trip`() {
        val chunk = FileServerMessages.UploadChunk(
            key = UploadKey.Source(sourceId = "source-1", fileId = "file-1"),
            offset = 4096,
            bytes = byteArrayOf(1, 2, 3, 4, 5),
        )

        val decoded = UploadChunkCodec.decode(UploadChunkCodec.encode(chunk))

        assertEquals(chunk.key, decoded.key)
        assertEquals(chunk.offset, decoded.offset)
        assertArrayEquals(chunk.bytes, decoded.bytes)
    }

    @Test
    fun `a one-shot key survives the round trip, with its header size exact`() {
        val chunk = FileServerMessages.UploadChunk(
            key = UploadKey.OneShot(transferId = "transfer-1", index = 7),
            offset = 1234,
            bytes = byteArrayOf(1, 2, 3),
        )

        val encoded = UploadChunkCodec.encode(chunk)
        val decoded = UploadChunkCodec.decode(encoded)

        assertEquals(chunk.key, decoded.key)
        assertEquals(chunk.offset, decoded.offset)
        assertArrayEquals(chunk.bytes, decoded.bytes)
        assertEquals(UploadChunkCodec.headerSize(chunk.key) + chunk.bytes.size, encoded.size)
    }

    @Test
    fun `ids outside ascii survive the round trip`() {
        val chunk = FileServerMessages.UploadChunk(
            key = UploadKey.Source(sourceId = "источник", fileId = "файл-📁"),
            offset = 0,
            bytes = ByteArray(0),
        )

        val decoded = UploadChunkCodec.decode(UploadChunkCodec.encode(chunk))

        assertEquals(chunk.key, decoded.key)
        assertEquals(0, decoded.bytes.size)
    }

    @Test
    fun `the header size is exactly what encoding adds around the bytes`() {
        val chunk = FileServerMessages.UploadChunk(
            key = UploadKey.Source(sourceId = "source-1", fileId = "file-1"),
            offset = 0,
            bytes = ByteArray(128),
        )

        val encoded = UploadChunkCodec.encode(chunk)

        // A sender sizes a chunk to the frame from this number; if it is short, every chunk splits.
        assertEquals(
            encoded.size,
            UploadChunkCodec.headerSize(chunk.key) + chunk.bytes.size,
        )
    }

    @Test
    fun `anything that is not a chunk is not mistaken for one`() {
        assertFalse(UploadChunkCodec.isUploadChunk(ByteArray(0)))
        assertFalse(UploadChunkCodec.isUploadChunk("{\"type\":\"x\"}".toByteArray()))
        assertFalse(UploadChunkCodec.isUploadChunk("uploadChun".toByteArray()))

        assertTrue(
            UploadChunkCodec.isUploadChunk(
                UploadChunkCodec.encode(
                    FileServerMessages.UploadChunk(UploadKey.Source("s", "f"), 0, ByteArray(0))
                )
            )
        )
    }

    @Test
    fun `a frame that is not a chunk is refused rather than decoded`() {
        val failure = runCatching { UploadChunkCodec.decode("not a chunk at all".toByteArray()) }
            .exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `a truncated frame fails as a protocol error`() {
        val encoded = UploadChunkCodec.encode(
            FileServerMessages.UploadChunk(UploadKey.Source("source-1", "file-1"), 0, byteArrayOf(1, 2, 3))
        )

        val failure = runCatching { UploadChunkCodec.decode(encoded.copyOf(encoded.size / 2)) }
            .exceptionOrNull()

        assertTrue(failure is NetworkException.Protocol)
    }

    @Test
    fun `a frame shorter than the magic fails rather than reading past the end`() {
        val failure = runCatching { UploadChunkCodec.decode("up".toByteArray()) }.exceptionOrNull()

        assertTrue(failure is NetworkException.Protocol || failure is IllegalArgumentException)
    }

    @Test
    fun `a declared string length that does not fit the frame is refused`() {
        val encoded = UploadChunkCodec.encode(
            FileServerMessages.UploadChunk(UploadKey.Source("source-1", "file-1"), 0, byteArrayOf(1))
        )

        // Overwrite the source id's length prefix with something far larger than the frame.
        val magic = UploadChunkCodec.headerSize(UploadKey.Source("", "")) - Int.SIZE_BYTES * 2 - Long.SIZE_BYTES
        val tampered = encoded.copyOf()
        tampered[magic] = 0x7F
        tampered[magic + 1] = 0x7F

        val failure = runCatching { UploadChunkCodec.decode(tampered) }.exceptionOrNull()

        assertTrue(failure is NetworkException.Protocol)
    }
}
