package com.fserver.core.sync.server

import android.content.ContextWrapper
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.gc.GarbageCollector
import com.fserver.core.network.NetworkController
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeRequirementsChecker
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.fileDto
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.fileops.FileDeleter
import com.fserver.core.sync.fileops.FileEvictor
import com.fserver.core.sync.fileops.FileMover
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.lease.SyncLeaseRegistry
import com.fserver.core.sync.lease.SyncModeReconciler
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.sync.runner.pass.PassCompletion
import com.fserver.core.sync.server.handler.FetchFilesHandler
import com.fserver.core.sync.server.handler.FileOperationHandler
import com.fserver.core.sync.server.handler.PublishIndexHandler
import com.fserver.core.sync.server.handler.SyncLeaseHandler
import com.fserver.core.sync.server.handler.upload.FileUploadHandler
import com.fserver.core.sync.server.handler.upload.UploadAdmission
import com.fserver.core.sync.server.handler.upload.UploadStaging
import com.fserver.core.sync.setup.SourceSetupExchange
import com.fserver.core.sync.transfer.FileUploader
import com.fserver.core.sync.transfer.RequestedDownloads
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.files.FilesNode
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.session.PeerSession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The dispatch table in front of every handler: session lifecycle, ordering, and the cap on what
 * one peer may have running at once.
 *
 * `NetworkController` is mocked rather than built: it is final and raises a live `NetworkNode` in
 * its constructor, while all this class wants from it is one flow of sessions. The slice itself
 * (`IncomingConnectionsManager`) is an interface, so that half is a plain fake.
 */
class PeerRequestServerTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(localDeviceId = LocalId, clock = clock)
    private val node by lazy {
        FilesNode.create(ContextWrapper(null), stagingDir = File(temp.root, "staging"))
    }
    private val index by lazy { LocalIndex(storage, node, clock) }
    private val staging by lazy { UploadStaging(storage, node, clock) }
    private val garbageCollector by lazy { GarbageCollector(storage, node, clock, background, FileEvictor(storage, node, index.writer)) }
    private val progress = SyncProgressReporter(clock)
    private val registry = SyncLeaseRegistry(clock, progress)
    private val incoming = FakeIncomingConnections()

    private val network = mockk<NetworkController>()

    private lateinit var root: File
    private lateinit var server: PeerRequestServer

    @Before
    fun setUp() = runBlocking {
        root = temp.newFolder("source-root")
        every { network.incomingConnections } returns incoming

        storage.sources.upsert(
            sourceEntry(
                id = SourceId,
                deviceId = PeerId,
                location = SourceLocation.Directory(root.absolutePath),
            )
        )
    }

    @After
    fun tearDown() = runBlocking {
        server.stop()
        background.cancel()
    }

    @Test
    fun `a request from a connected peer is answered`() = runTest {
        start()
        val session = connect()
        val replies = FakePeerSession.Replies()

        session.deliver(FileServerMessages.FetchFiles.Request(SourceId), replies.channel)

        awaitReply(replies)
        assertTrue(replies.messages.single() is FileServerMessages.FetchFiles.FilesList)
    }

    @Test
    fun `a peer that reconnects is served by the new session, not the dead one`() = runTest {
        start()
        val first = connect()
        val second = connect(replacing = first)

        val onSecond = FakePeerSession.Replies()
        second.deliver(FileServerMessages.FetchFiles.Request(SourceId), onSecond.channel)
        awaitReply(onSecond)

        val onFirst = FakePeerSession.Replies()
        val delivered = runCatching {
            first.deliver(FileServerMessages.FetchFiles.Request(SourceId), onFirst.channel)
        }
        pause(200)

        // The reconnect took the slot, so the dead session is done: either it no longer accepts
        // anything, or what it accepted is never answered. Both mean the old job is gone.
        assertEquals(1, onSecond.messages.size)
        assertTrue(delivered.isFailure || onFirst.messages.isEmpty())
    }

    @Test
    fun `a session that ends releases every lease that peer held`() = runTest {
        start()
        val session = connect()
        assertTrue(registry.grantToPeer(SourceId, PeerId, "peer-lease", LocalId))

        session.close()

        awaitTrue { registry.beginAcquire(SourceId) != null }
    }

    @Test
    fun `a session that ends parks its uploads in staging for the peer to resume`() = runTest {
        start()
        val session = connect()
        val replies = FakePeerSession.Replies()

        session.deliver(
            FileServerMessages.Upload.Init(
                sourceId = SourceId,
                file = fileDto(id = FileIdValue, sourceId = SourceId, path = FileName, size = 100),
            ),
            replies.channel,
        )
        awaitReply(replies)

        session.deliver(
            FileServerMessages.UploadChunk(SourceId, FileIdValue, 0, "half a file".toByteArray())
        )
        val key = IndexedFileKey(fileId = FileIdValue, sourceId = SourceId)
        awaitTrue { File(temp.root, "staging/$SourceId/$FileIdValue/data").length() == 11L }

        session.close()

        // Flushed and recorded, so the next Init picks up from here; the source never saw a byte.
        awaitTrue { storage.uploads.find(key)?.committedOffset == 11L }
        assertTrue(root.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a peer at the concurrency cap is refused rather than queued`() = runTest {
        val held = CompletableDeferred<Unit>()
        val blocking = mockk<FetchFilesHandler>()
        coEvery { blocking.handle(any(), any(), any()) } coAnswers { held.await() }

        start(fetchFiles = blocking)
        val session = connect()

        repeat(MaxConcurrentRequests) {
            session.deliver(FileServerMessages.FetchFiles.Request(SourceId), { Result.success(Unit) })
        }

        val refused = FakePeerSession.Replies()
        session.deliver(FileServerMessages.FetchFiles.Request(SourceId), refused.channel)

        // Refused on the spot rather than parked: parking the collector would drop the upload
        // chunks queued behind it, which `:net` does not re-send.
        awaitReply(refused)
        assertEquals("Receiver busy", refused.only<FileServerMessages.FetchFiles.Failed>().reason)

        held.complete(Unit)
    }

    @Test
    fun `a handler that throws does not take the session down`() = runTest {
        val flaky = mockk<FetchFilesHandler>()
        coEvery { flaky.handle(any(), any(), any()) } throws IllegalStateException("boom")

        start(fetchFiles = flaky)
        val session = connect()

        val first = FakePeerSession.Replies()
        session.deliver(FileServerMessages.FetchFiles.Request(SourceId), first.channel)
        pause(200)

        val second = FakePeerSession.Replies()
        session.deliver(
            FileServerMessages.AcquireSyncLease.Request(SourceId, "lease-1", sourceEntry().syncMode.toDto()),
            second.channel,
        )

        awaitReply(second)
        assertTrue(second.messages.single() is FileServerMessages.AcquireSyncLease.Granted)
    }

    private suspend fun start(fetchFiles: FetchFilesHandler = realFetchFiles()) {
        server = PeerRequestServer(
            network = network,
            leaseRegistry = registry,
            sourceSetup = SourceSetupExchange(storage, mockk(relaxed = true), clock),
            fetchFiles = fetchFiles,
            publishedIndexes = PublishIndexHandler(authorizer(), storage, clock, HybridLogicalClock(storage, clock)),
            leases = SyncLeaseHandler(
                authorizer(),
                storage,
                registry,
                SyncModeReconciler(storage),
                PassCompletion(storage, mockk(relaxed = true), garbageCollector, background, clock),
            ),
            fileOperations = FileOperationHandler(
                authorizer = authorizer(),
                storage = storage,
                localHasher = index.hasher,
                indexWriter = index.writer,
                fileDeleter = FileDeleter(storage, node, index.writer),
                fileUploader = FileUploader(
                index.writer,
                PeerIndexFetcher(storage, mockk(relaxed = true), clock, HybridLogicalClock(storage, clock)),
                node,
                progress,
            ),
                fileMover = FileMover(storage, node, index.writer),
            ),
            uploads = FileUploadHandler(
                authorizer(),
                UploadAdmission(storage, RequestedDownloads()),
                index.writer,
                node,
                staging,
                clock,
                progress,
            ),
            devicesRepository = mockk(relaxed = true),
            requirementsChecker = FakeRequirementsChecker(),
            backgroundScope = background,
        )

        assertNotNull(server.start().getOrThrow())
    }

    private fun authorizer() = SourceAuthorizer(storage)

    private fun realFetchFiles() = FetchFilesHandler(
        authorizer = authorizer(),
        localIndexer = index.indexer,
    )

    /** Publishes a session the way the node would, and waits until the server has taken it up. */
    private suspend fun connect(replacing: FakePeerSession? = null): FakePeerSession {
        val session = FakePeerSession(identity = peerIdentity(PeerId))
        incoming.publish(listOfNotNull(replacing, session))

        val probe = FakePeerSession.Replies()
        session.deliver(
            FileServerMessages.AcquireSyncLease.Request("not-a-source", "probe", sourceEntry().syncMode.toDto()),
            probe.channel,
        )
        awaitReply(probe)

        return session
    }

    private suspend fun awaitReply(replies: FakePeerSession.Replies) =
        awaitTrue { replies.messages.isNotEmpty() }

    /**
     * Waits on a real dispatcher on purpose: the server runs on real threads, and `runTest`'s
     * scheduler would skip every delay instantly and answer the question before the work started.
     */
    private suspend fun awaitTrue(condition: suspend () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (!condition()) delay(20)
            }
        }
    }

    /** Real time, for the cases that have to prove nothing happened. */
    private suspend fun pause(millis: Long) = withContext(Dispatchers.Default) { delay(millis) }

    private class FakeIncomingConnections : IncomingConnectionsManager<FileServerMessages> {
        private val state = MutableStateFlow<List<PeerSession<FileServerMessages>>>(emptyList())

        override val sessions: StateFlow<List<PeerSession<FileServerMessages>>> = state
        override val incoming: Flow<IncomingConnectionsManager.IncomingRequest> = emptyFlow()

        override fun session(deviceId: String): PeerSession<FileServerMessages>? =
            state.value.find { it.identity.deviceId == deviceId }

        fun publish(sessions: List<PeerSession<FileServerMessages>>) {
            state.value = sessions
        }
    }

    private companion object {
        const val SourceId = "source-1"
        const val LocalId = "device-local"
        const val PeerId = "device-peer"
        const val FileIdValue = "file-1"
        const val FileName = "photo.jpg"

        /** Mirrors `PeerRequestServer.MaxConcurrentRequests`, which is private. */
        const val MaxConcurrentRequests = 5
    }
}
