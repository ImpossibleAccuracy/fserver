package com.fserver.core.sync.server.handler

import android.content.ContextWrapper
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.VersionDto
import com.fserver.core.network.dictionary.dto.toIndexed
import com.fserver.core.support.FakeRequirementsChecker
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.indexedFile
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.runner.FileUploader
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.files.FilesNode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * What a peer may have done to a file this device holds.
 *
 * Every one of these acts on bytes, so the check that the asking device is the one the source
 * syncs with is the only thing between an authenticated peer and someone else's files.
 */
class FileOperationHandlerTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val node = FilesNode.create(ContextWrapper(null))
    private val indexer =
        LocalChangesIndexer(storage, node, FakeRequirementsChecker(), clock, HybridLogicalClock(storage, clock))
    private val progress = SyncProgressReporter(clock)

    private lateinit var root: File
    private lateinit var file: File
    private lateinit var handler: FileOperationHandler

    private val owner = session(OwnerId)
    private val stranger = session(StrangerId)

    @Before
    fun setUp() = runBlocking {
        root = temp.newFolder("source-root")
        file = File(root, FileName).apply { writeText(Contents) }

        handler = FileOperationHandler(
            authorizer = SourceAuthorizer(storage),
            storage = storage,
            node = node,
            localIndexer = indexer,
            fileUploader = FileUploader(indexer, node, progress),
        )

        storage.sources.upsert(
            sourceEntry(
                id = SourceId,
                deviceId = OwnerId,
                location = SourceLocation.Directory(root.absolutePath),
            )
        )
        storage.index.markProcessed(
            listOf(
                indexedFile(
                    sourceId = SourceId,
                    fileId = FileIdValue,
                    path = FileName,
                    locator = file.absolutePath,
                    size = Contents.length.toLong(),
                )
            )
        )
    }

    @Test
    fun `a delete the paired device asked for removes the file and records a tombstone`() =
        runTest {
            handler.handle(owner, RemoteOperation.File.Delete(key(), version = null))

            assertTrue(!file.exists())
            assertTrue(storage.index.findFile(key())?.state is LocalIndexedFile.State.Deleted)
        }

    @Test
    fun `a delete records the peer's deletion version, not a new one of ours`() = runTest {
        val version = VersionDto(vector = mapOf(OwnerId to 2L), hlc = 7, originDevice = OwnerId)

        handler.handle(owner, RemoteOperation.File.Delete(key(), version))

        assertEquals(version.toIndexed(), storage.index.findFile(key())?.version)
    }

    @Test
    fun `a delete from a device the source does not sync with touches nothing`() = runTest {
        val failure = runCatching { handler.handle(stranger, RemoteOperation.File.Delete(key(), version = null)) }
            .exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(file.exists())
        assertTrue(storage.index.findFile(key())?.state is LocalIndexedFile.State.Present)
    }

    @Test
    fun `a delete of a file this source never indexed is refused`() = runTest {
        val failure = runCatching {
            handler.handle(owner, RemoteOperation.File.Delete(IndexedFileKey("ghost", SourceId), version = null))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(file.exists())
    }

    @Test
    fun `a download for another device's source sends no bytes`() = runTest {
        val failure = runCatching { handler.handle(stranger, RemoteOperation.File.Download(key())) }
            .exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(stranger.sent.isEmpty())
        assertTrue(stranger.requested.isEmpty())
    }

    @Test
    fun `a download for the paired device streams the file back`() = runTest {
        handler.handle(owner, RemoteOperation.File.Download(key()))

        val chunks = owner.sent.filterIsInstance<FileServerMessages.UploadChunk>()
        val streamed = chunks.sortedBy { it.offset }.fold(ByteArray(0)) { acc, c -> acc + c.bytes }

        assertEquals(Contents, String(streamed))
    }

    @Test
    fun `a hash request from the paired device fills the index in`() = runTest {
        handler.handle(owner, RemoteOperation.File.Hash(key()))

        assertEquals(sha256(Contents.toByteArray()), storage.index.findFile(key())?.hash?.value)
    }

    @Test
    fun `a hash request from any other device is refused`() = runTest {
        val failure = runCatching { handler.handle(stranger, RemoteOperation.File.Hash(key())) }
            .exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertNull(storage.index.findFile(key())?.hash)
    }

    private fun key() = IndexedFileKey(fileId = FileIdValue, sourceId = SourceId)

    private fun session(deviceId: String) = FakePeerSession(
        identity = peerIdentity(deviceId),
        responder = { message ->
            (message as? FileServerMessages.OperationWithConfirmation.Request)?.let {
                FileServerMessages.OperationWithConfirmation.Completed(it.operationId)
            }
        },
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SourceId = "source-1"
        const val OwnerId = "device-owner"
        const val StrangerId = "device-stranger"
        const val FileIdValue = "file-1"
        const val FileName = "photo.jpg"
        const val Contents = "the actual file contents"
    }
}
