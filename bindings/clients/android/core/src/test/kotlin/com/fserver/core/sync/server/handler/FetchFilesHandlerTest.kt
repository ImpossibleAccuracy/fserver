package com.fserver.core.sync.server.handler

import android.content.ContextWrapper
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeRequirementsChecker
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.files.FilesNode
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Listing a source for the peer that asked - and refusing to confirm one that is not its own. */
class FetchFilesHandlerTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val node = FilesNode.create(ContextWrapper(null))

    private lateinit var handler: FetchFilesHandler

    @Before
    fun setUp() = runBlocking {
        val root = temp.newFolder("source-root")
        File(root, "photo.jpg").writeText("bytes")

        handler = FetchFilesHandler(
            authorizer = SourceAuthorizer(storage),
            localIndexer = LocalIndex(storage, node, clock).indexer,
        )

        storage.sources.upsert(
            sourceEntry(
                id = SourceId,
                deviceId = OwnerId,
                location = SourceLocation.Directory(root.absolutePath),
            )
        )
    }

    @Test
    fun `the paired device gets the list`() = runTest {
        val replies = FakePeerSession.Replies()
        val session = FakePeerSession(identity = peerIdentity(OwnerId))

        handler.handle(
            event = PeerSession.Inbound(request(), replies.channel),
            message = request(),
            session = session,
        )

        val list = replies.only<FileServerMessages.FetchFiles.FilesList>()
        assertEquals(listOf("photo.jpg"), list.files.map { it.path })
    }

    @Test
    fun `any other device is answered with a failure, never with the contents`() = runTest {
        val replies = FakePeerSession.Replies()
        val session = FakePeerSession(identity = peerIdentity(StrangerId))

        handler.handle(
            event = PeerSession.Inbound(request(), replies.channel),
            message = request(),
            session = session,
        )

        // Answered rather than dropped, so the peer fails now - but with nothing in it.
        assertTrue(replies.only<FileServerMessages.FetchFiles.Failed>().sourceId == SourceId)
    }

    @Test
    fun `a request with no reply channel is dropped without touching the source`() = runTest {
        handler.handle(
            event = PeerSession.Inbound(request(), null),
            message = request(),
            session = FakePeerSession(identity = peerIdentity(OwnerId)),
        )

        assertTrue(storage.index.current.isEmpty())
    }

    private fun request() = FileServerMessages.FetchFiles.Request(SourceId)

    private companion object {
        const val SourceId = "source-1"
        const val OwnerId = "device-owner"
        const val StrangerId = "device-stranger"
    }
}
