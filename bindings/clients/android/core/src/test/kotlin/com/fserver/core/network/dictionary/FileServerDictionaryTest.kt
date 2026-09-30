package com.fserver.core.network.dictionary

import com.fserver.core.network.dictionary.codec.UploadChunkCodec
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.network.dictionary.dto.SourceMetadataDto
import com.fserver.core.network.dictionary.dto.SyncModeDto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FileServerDictionaryTest {

    private val codec = FileServerDictionary().codec

    @Test
    fun `a chunk survives the round trip through the dictionary`() {
        val chunk = FileServerMessages.UploadChunk(
            key = UploadKey.Source(sourceId = "5f7c0f2e-0e0a-4e5e-9a1d-3f9f2b1c4d55", fileId = "a".repeat(64)),
            offset = 4_294_967_296,
            bytes = Random(7).nextBytes(9_000),
        )

        val decoded = codec.decode(codec.encode(chunk))

        assertTrue(decoded is FileServerMessages.UploadChunk)
        decoded as FileServerMessages.UploadChunk
        assertEquals(chunk.key, decoded.key)
        assertEquals(chunk.offset, decoded.offset)
        assertArrayEquals(chunk.bytes, decoded.bytes)
    }

    @Test
    fun `an empty chunk survives the round trip`() {
        val chunk = FileServerMessages.UploadChunk(
            key = UploadKey.Source(sourceId = "source", fileId = "file"),
            offset = 0,
            bytes = ByteArray(0),
        )

        val decoded = codec.decode(codec.encode(chunk)) as FileServerMessages.UploadChunk

        assertArrayEquals(ByteArray(0), decoded.bytes)
    }

    @Test
    fun `everything else still travels as json`() {
        val message = FileServerMessages.ConfigureSource.Request(
            sourceId = "5f7c0f2e",
            label = "DCIM/Projects",
            syncMode = SyncModeDto.Mirror(),
            metadata = SourceMetadataDto(storageKind = SourceMetadataDto.StorageKind.Folder, storagePath = "DCIM/Projects", files = 2, bytes = 2048, usedPercent = 12.5f),
        )

        val encoded = codec.encode(message)

        assertEquals('{', encoded.decodeToString().first())
        assertEquals(message, codec.decode(encoded))
    }

    @Test
    fun `the declared header size is what the codec actually writes`() {
        val sourceId = "5f7c0f2e-0e0a-4e5e-9a1d-3f9f2b1c4d55"
        val fileId = "имя-с-не-ascii"
        val payload = 1_024

        val encoded = codec.encode(
            FileServerMessages.UploadChunk(
                key = UploadKey.Source(sourceId = sourceId, fileId = fileId),
                offset = 17,
                bytes = ByteArray(payload),
            )
        )

        assertEquals(
            UploadChunkCodec.headerSize(UploadKey.Source(sourceId, fileId)) + payload,
            encoded.size,
        )
    }
}
