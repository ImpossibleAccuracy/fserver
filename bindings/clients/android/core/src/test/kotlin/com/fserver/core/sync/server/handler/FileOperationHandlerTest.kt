package com.fserver.core.sync.server.handler

import android.content.ContextWrapper
import com.fserver.common.exception.SyncException
import com.fserver.common.model.ContentHash
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.ContentHashDto
import com.fserver.core.network.dictionary.dto.FileRecordDto
import com.fserver.core.network.dictionary.dto.VersionDto
import com.fserver.core.network.dictionary.dto.toIndexed
import com.fserver.core.support.FakePeerSession
import com.fserver.core.support.FakeRequirementsChecker
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.indexedFile
import com.fserver.core.support.peerIdentity
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.fileops.FileDeleter
import com.fserver.core.sync.fileops.FileMover
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.sync.transfer.FileUploader
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.files.FilesNode
import io.mockk.mockk
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
import kotlin.time.Instant

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
    private val index = LocalIndex(storage, node, clock)
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
    fun `a one-way initiator refuses its follower's delete and version changes`() = runTest {
        val source = storage.sources.findById(SourceId)!!
        storage.sources.upsert(
            source.copy(syncMode = SyncMode.AutoUpload(ignoreFilesBefore = null), role = SourceEntry.Role.Initiator)
        )
        val version = VersionDto(vector = mapOf(OwnerId to 2L), hlc = 7, originDevice = OwnerId)

        val delete = runCatching { handler.handle(owner, RemoteOperation.File.Delete(key(), version = null)) }
        val adopt = runCatching { handler.handle(owner, RemoteOperation.File.AdoptVersion(key(), version, expected = null)) }

        assertTrue(delete.exceptionOrNull() is SyncException.ModeForbiddenException)
        assertTrue(adopt.exceptionOrNull() is SyncException.ModeForbiddenException)
        assertTrue(file.exists())
        assertEquals(null, storage.index.findFile(key())?.version)
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
    fun `a download the peer already holds part of resumes from there, hashing the whole file`() = runTest {
        val resuming = session(OwnerId, resumeFrom = 5)

        handler.handle(resuming, RemoteOperation.File.Download(key()))

        val chunks = resuming.sent.filterIsInstance<FileServerMessages.UploadChunk>()
        val complete = resuming.requested.filterIsInstance<FileServerMessages.Upload.Complete>().single()

        assertEquals(5L, chunks.first().offset)
        assertEquals(Contents.substring(5), String(chunks.fold(ByteArray(0)) { acc, c -> acc + c.bytes }))
        assertEquals(sha256(Contents.toByteArray()), complete.hash)
    }

    @Test
    fun `a download for the paired device streams the file back`() = runTest {
        handler.handle(owner, RemoteOperation.File.Download(key()))

        val chunks = owner.sent.filterIsInstance<FileServerMessages.UploadChunk>()
        val streamed = chunks.sortedBy { it.offset }.fold(ByteArray(0)) { acc, c -> acc + c.bytes }

        assertEquals(Contents, String(streamed))
    }

    @Test
    fun `a finished upload records the file in the remote index`() = runTest {
        handler.handle(owner, RemoteOperation.File.Download(key()))

        val recorded = storage.remoteIndex.files(SourceId).single()

        assertEquals(FileIdValue, recorded.fileId)
        assertEquals(sha256(Contents.toByteArray()), recorded.hash?.value)
        assertEquals(OwnerId, storage.remoteIndex.attributedTo[SourceId])
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

    @Test
    fun `a move renames the file and indexes both paths under the peer's versions`() = runTest {
        hashIndexed()
        val version = VersionDto(vector = mapOf(OwnerId to 1L), hlc = 7, originDevice = OwnerId)
        val deleted = VersionDto(vector = mapOf(OwnerId to 2L), hlc = 8, originDevice = OwnerId)

        handler.handle(owner, move(target = target(version), deletedVersion = deleted))

        val moved = storage.index.findFile(IndexedFileKey(MovedIdValue, SourceId))
        assertTrue(!file.exists())
        assertEquals(Contents, File(root, MovedName).readText())
        assertEquals(MovedName, moved?.path)
        assertEquals(version.toIndexed(), moved?.version)
        assertEquals(sha256(Contents.toByteArray()), moved?.hash?.value)
        assertTrue(storage.index.findFile(key())?.state is LocalIndexedFile.State.Deleted)
        assertEquals(deleted.toIndexed(), storage.index.findFile(key())?.version)
    }

    @Test
    fun `a move over a file not indexed yet is refused and touches nothing`() = runTest {
        hashIndexed()
        File(root, MovedName).writeText("not indexed yet")

        val failure = runCatching { handler.handle(owner, move(target = target(version = null))) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("not indexed yet", File(root, MovedName).readText())
        assertTrue(file.exists())
    }

    @Test
    fun `a move of bytes that changed since planned is refused`() = runTest {
        hashIndexed()

        val failure = runCatching {
            handler.handle(owner, move(target = target(version = null), expected = "other"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(file.exists())
        assertTrue(!File(root, MovedName).exists())
    }

    @Test
    fun `a one-way initiator refuses its follower's move`() = runTest {
        hashIndexed()
        val source = storage.sources.findById(SourceId)!!
        storage.sources.upsert(
            source.copy(syncMode = SyncMode.AutoUpload(ignoreFilesBefore = null), role = SourceEntry.Role.Initiator)
        )

        val failure = runCatching { handler.handle(owner, move(target = target(version = null))) }.exceptionOrNull()

        assertTrue(failure is SyncException.ModeForbiddenException)
        assertTrue(file.exists())
    }

    private suspend fun hashIndexed() {
        val row = storage.index.findFile(key())!!
        storage.index.markProcessed(listOf(row.copy(hash = ContentHash(sha256(Contents.toByteArray()), "SHA-256"))))
    }

    private fun move(
        target: FileRecordDto,
        expected: String = sha256(Contents.toByteArray()),
        deletedVersion: VersionDto? = null,
    ) = RemoteOperation.File.Move(
        key = key(),
        expected = ContentHashDto(value = expected, algorithm = "SHA-256"),
        target = target,
        deletedVersion = deletedVersion,
    )

    private fun target(version: VersionDto?) = FileRecordDto(
        id = MovedIdValue,
        sourceId = SourceId,
        path = MovedName,
        state = FileRecordDto.State.Present(),
        content = ContentHashDto(value = sha256(Contents.toByteArray()), algorithm = "SHA-256"),
        metadata = FileRecordDto.Metadata(
            size = Contents.length.toLong(),
            lastModified = Instant.fromEpochMilliseconds(file.lastModified()),
            version = version,
        ),
    )

    private fun key() = IndexedFileKey(fileId = FileIdValue, sourceId = SourceId)

    /** A peer that takes every upload, holding [resumeFrom] bytes of it before the first chunk. */
    private fun session(deviceId: String, resumeFrom: Long = 0) = FakePeerSession(
        identity = peerIdentity(deviceId),
        responder = { message ->
            when (message) {
                is FileServerMessages.OperationWithConfirmation.Request ->
                    FileServerMessages.OperationWithConfirmation.Completed(message.operationId)

                is FileServerMessages.Upload.Init -> FileServerMessages.Upload.Received(message.key, resumeFrom)
                is FileServerMessages.Upload.Status -> FileServerMessages.Upload.Received(message.key, resumeFrom)
                is FileServerMessages.Upload.Complete -> FileServerMessages.Upload.Completed(message.key)
                else -> null
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
        const val MovedIdValue = "file-2"
        const val MovedName = "renamed.jpg"
        const val Contents = "the actual file contents"
    }
}
