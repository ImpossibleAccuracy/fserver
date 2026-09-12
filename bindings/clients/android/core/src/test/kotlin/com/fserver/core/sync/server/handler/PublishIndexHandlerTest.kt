package com.fserver.core.sync.server.handler

import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.FileRecordDto
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.fileDto
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.server.SourceAuthorizer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The peer's own index, taken on trust about the peer and on nothing else.
 *
 * Nothing is answered here, so a refusal is silent - which is exactly why the check has to be
 * asserted rather than eyeballed.
 */
class PublishIndexHandlerTest {

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val handler = PublishIndexHandler(SourceAuthorizer(storage), storage, clock)

    @Before
    fun setUp() = runBlocking {
        storage.sources.upsert(sourceEntry(id = SourceId, deviceId = OwnerId))
    }

    @Test
    fun `what the paired device published is recorded against its source`() = runTest {
        handler.handle(
            message = FileServerMessages.PublishIndex(
                sourceId = SourceId,
                files = listOf(fileDto(id = "file-1", sourceId = SourceId, path = "a.jpg")),
            ),
            session = FakePeerSession(identity = peerIdentity(OwnerId)),
        )

        assertEquals(listOf("a.jpg"), storage.remoteIndex.files(SourceId).map { it.path })
        assertEquals(OwnerId, storage.remoteIndex.attributedTo[SourceId])
    }

    @Test
    fun `a device the source does not sync with cannot overwrite the record`() = runTest {
        handler.handle(
            message = FileServerMessages.PublishIndex(
                sourceId = SourceId,
                files = listOf(fileDto(id = "file-1", sourceId = SourceId, path = "a.jpg")),
            ),
            session = FakePeerSession(identity = peerIdentity(OwnerId)),
        )

        handler.handle(
            message = FileServerMessages.PublishIndex(sourceId = SourceId, files = emptyList()),
            session = FakePeerSession(identity = peerIdentity(StrangerId)),
        )

        assertEquals(listOf("a.jpg"), storage.remoteIndex.files(SourceId).map { it.path })
    }

    @Test
    fun `a file the peer evicted is recorded as evicted, never as deleted`() = runTest {
        handler.handle(
            message = FileServerMessages.PublishIndex(
                sourceId = SourceId,
                files = listOf(
                    fileDto(
                        id = "file-1",
                        sourceId = SourceId,
                        path = "a.jpg",
                        state = FileRecordDto.State.Evicted(TestEpoch),
                    )
                ),
            ),
            session = FakePeerSession(identity = peerIdentity(OwnerId)),
        )

        val recorded = storage.remoteIndex.files(SourceId).single()
        assertTrue(recorded.state is LocalIndexedFile.State.Evicted)
    }

    private companion object {
        const val SourceId = "source-1"
        const val OwnerId = "device-owner"
        const val StrangerId = "device-stranger"
    }
}
