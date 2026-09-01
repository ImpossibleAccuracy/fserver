package com.fserver.core.sync.remote

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.IdGenerator
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.NetworkController
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.FileServerMessages.Response.OperationCompleted
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.network.dictionary.dto.toFileRecord
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.toFileRecord
import com.fserver.core.sync.index.toIndexed
import com.fserver.core.sync.runner.FileUploader
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileRecord
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The answering half of sync: one long-lived listener that serves what peers ask of this device.
 *
 * [PeerIndexFetcher] and `FileActionRunner` drive a pass we started; every message arriving from a
 * pass the *peer* started lands here. Started by the host through `FServerCore.startServing`, and
 * lives as long as the engine - a request can arrive with no screen open.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class PeerRequestServer(
    private val network: NetworkController,
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val localIndexer: LocalChangesIndexer,
    private val fileUploader: FileUploader,
    private val timeProvider: TimeProvider,
    private val backgroundScope: BackgroundScope,
) {
    private val isListening = AtomicBoolean(false)
    private val jobsLock = Mutex()
    private val jobs = ConcurrentHashMap<PeerIdentity, Job>()

    private val sessionContexts = ConcurrentHashMap<PeerIdentity, SessionContext>()

    /** Idempotent: repeated calls from the host keep the one listener already running. */
    fun start(): Job? {
        if (!isListening.compareAndSet(false, true)) return null

        return backgroundScope.launch {
            network.incomingConnections.sessions.collect { sessions ->
                for (session in sessions) {
                    val peer = session.identity

                    jobsLock.withLock {
                        jobs.computeIfAbsent(peer) {
                            backgroundScope.launch {
                                try {
                                    serve(session)
                                } finally {
                                    jobs.remove(peer)
                                    sessionContexts.remove(peer)
                                }
                            }
                        }
                    }
                }
            }
        }.also {
            it.invokeOnCompletion {
                isListening.compareAndSet(true, false)
            }
        }
    }

    private suspend fun serve(session: PeerSession<FileServerMessages>) {
        // Limit concurrent requests, to prevent a lot of long-running operations
        val semaphore = Semaphore(3)

        session.incoming.collect { event ->
            semaphore.acquire()

            backgroundScope.launch {
                // Run in separate coroutine, so long-running operations don't block each other
                try {
                    dispatch(session, event)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to serve ${event.message}")
                } finally {
                    semaphore.release()
                }
            }
        }
    }

    private suspend fun dispatch(
        session: PeerSession<FileServerMessages>,
        event: PeerSession.Inbound<FileServerMessages>,
    ) {
        when (val message = event.message) {
            // Answers to requests we sent; the caller awaiting them handles those.
            is FileServerMessages.Response -> Unit

            is FileServerMessages.FetchFiles -> {
                sendFiles(event, message, session)
                return
            }

            is FileServerMessages.UploadChunk -> {
                // TODO: read the chunk and write to file
            }

            is FileServerMessages.OperationWithConfirmation -> {
                val result =
                    runCatchingCancellable { runOperation(session, message.instance) }

                val reply = event.reply
                if (reply == null) {
                    Timber.w("Cannot reply to ${message.instance} from ${session.route.deviceId}: no reply channel")
                    return
                }

                result.fold(
                    onSuccess = {
                        reply(OperationCompleted(message.operationId))
                    },
                    onFailure = { t ->
                        reply(
                            FileServerMessages.Response.OperationFailed(
                                operationId = message.operationId,
                                reason = t.message ?: "Unknown error"
                            )
                        )
                    }
                )
            }
        }
    }

    /** Send local indexed files to the peer. */
    private suspend fun sendFiles(
        event: PeerSession.Inbound<FileServerMessages>,
        message: FileServerMessages.FetchFiles,
        session: PeerSession<FileServerMessages>,
    ) {
        val reply = event.reply
        if (reply == null) {
            Timber.w("Cannot reply to ${message.sourceId} from ${session.route.deviceId}: no reply channel")
            return
        }

        val source = storage.sources.findById(message.sourceId)
        if (source == null) {
            // TODO: send error response to peer, so it knows the source is gone
            Timber.w("Cannot reply to ${message.sourceId} from ${session.route.deviceId}: source not found")
            return
        }

        val files = localIndexer.refresh(source)

        reply(
            FileServerMessages.Response.FilesList(
                files.map { it.toDto() }
            )
        )
    }

    /** Run a remote operation that the peer requested. */
    private suspend fun runOperation(
        session: PeerSession<FileServerMessages>,
        operation: RemoteOperation
    ) {
        when (operation) {
            is RemoteOperation.File -> runFileOperation(session, operation)

            is RemoteOperation.Upload -> runUploadOperation(session, operation)
        }
    }

    private suspend fun runFileOperation(
        session: PeerSession<FileServerMessages>,
        operation: RemoteOperation.File,
    ) {
        val source = storage.sources.findById(operation.key.sourceId)
            ?: throw IllegalArgumentException("Source ${operation.key.sourceId} not found")

        val file = storage.index.findFile(operation.key)
            ?: throw IllegalArgumentException("File ${operation.key} not found in source ${operation.key.sourceId}")

        when (operation) {
            is RemoteOperation.File.Hash -> localIndexer.hashFile(source, file)

            is RemoteOperation.File.Delete -> {
                val fs = node.openSource(source.location.toFiles())
                val deleted = fs.deleteFile(file.locator)

                if (!deleted) {
                    Timber.w("Failed to delete file ${file.id} at ${file.path} from source ${source.id}")
                }
            }

            is RemoteOperation.File.Download -> {
                // Peer requests us to send them the file
                fileUploader.uploadFile(
                    file = file.toFileRecord(),
                    source = source,
                    session = session,
                )
            }
        }
    }

    private suspend fun runUploadOperation(
        session: PeerSession<FileServerMessages>,
        operation: RemoteOperation.Upload,
    ) {
        when (operation) {
            is RemoteOperation.Upload.Init -> {
                val source = storage.sources.findById(operation.sourceId)
                    ?: throw IllegalArgumentException("Source ${operation.sourceId} not found")

                val key = IndexedFileKey(operation.file.id, source.id)
                val file = operation.file.toFileRecord()

                val context = SessionContext.UploadContext(file)

                sessionContexts.computeIfAbsent(session.identity) { SessionContext() }
                    .uploads[key] = context
            }

            is RemoteOperation.Upload.UploadCompleted -> {
                val source = storage.sources.findById(operation.key.sourceId)
                    ?: throw IllegalArgumentException("Source ${operation.key.sourceId} not found")

                val context = sessionContexts[session.identity]?.uploads?.remove(operation.key)
                    ?: throw IllegalArgumentException("Upload context for file ${operation.key.fileId} not found")

                val saved = storage.index.findFile(operation.key)

                val indexedFile = context.file
                    .copy(
                        content = ContentHash(
                            value = operation.hash,
                            algorithm = operation.algorithm,
                        )
                    )
                    .toIndexed(
                        id = saved?.id ?: IdGenerator.nextId,
                        sourceId = source.id,
                        locator = "", // TODO
                        currentTime = timeProvider.now(),
                    )

                storage.index.markProcessed(listOf(indexedFile))
            }
        }
    }
}

/** Holds per-peer state for the duration of a session. */
private data class SessionContext(
    val uploads: MutableMap<IndexedFileKey, UploadContext> = ConcurrentHashMap(),
) {
    data class UploadContext(
        val file: FileRecord,
    )
}
