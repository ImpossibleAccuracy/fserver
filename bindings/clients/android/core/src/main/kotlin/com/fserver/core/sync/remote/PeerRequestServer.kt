package com.fserver.core.sync.remote

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.IdGenerator
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.NetworkController
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.network.dictionary.dto.toFileRecord
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.core.sync.index.IndexedFile
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The answering half of sync: one long-lived listener that serves what peers ask of this device.
 *
 * [PeerIndexFetcher] and `FileActionRunner` drive a pass we started; every message arriving from a
 * pass the *peer* started lands here. Started by the host through `FServerCore.startServing`, and
 * lives as long as the engine - a request can arrive with no screen open.
 *
 * Everything served here is addressed by an id the *peer* chose, so every entry point resolves its
 * source through [authorizedSource] rather than [FServerStorage.sources] directly.
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

    /** Guarded by [jobsLock]. Holds the session too, so a reconnect can be told from a re-emit. */
    private val jobs = HashMap<PeerIdentity, ServedSession>()

    private var listenerJob: Job? = null

    /** Idempotent: repeated calls from the host keep the one listener already running. */
    fun start(): Job? {
        if (!isListening.compareAndSet(false, true)) return null

        val job = backgroundScope.launch {
            network.incomingConnections.sessions.collect { sessions ->
                for (session in sessions) {
                    register(session)
                }
            }
        }

        listenerJob = job

        job.invokeOnCompletion {
            listenerJob = null
            isListening.compareAndSet(true, false)
        }

        return job
    }

    /**
     * Stops serving and drops every request still in flight. [start] works again afterward.
     */
    suspend fun stop() {
        listenerJob?.cancelAndJoin()

        val running = jobsLock.withLock { jobs.values.toList().also { jobs.clear() } }
        running.forEach { it.job.cancelAndJoin() }
    }

    /**
     * Binds one serving coroutine to one session.
     *
     * The registry is keyed by peer but compares sessions: a peer that reconnects arrives as a
     * *different* [PeerSession], and keying on identity alone would leave the new one unserved
     * behind the dead job still holding the slot.
     */
    private suspend fun register(session: PeerSession<FileServerMessages>) {
        val peer = session.identity

        jobsLock.withLock {
            val existing = jobs[peer]
            if (existing != null) {
                if (existing.session === session) return@withLock

                Timber.i("Device ${peer.deviceId} reconnected; dropping the previous session")
                existing.job.cancel()
            }

            val job = backgroundScope.launch {
                try {
                    serve(session)
                } finally {
                    withContext(NonCancellable) {
                        // Only if a reconnect has not already claimed the slot.
                        jobsLock.withLock {
                            if (jobs[peer]?.session === session) jobs.remove(peer)
                        }
                    }
                }
            }

            jobs[peer] = ServedSession(session, job)
        }
    }

    /**
     * Reads one session until it ends.
     *
     * Children are launched on this scope rather than the engine scope, so cancelling the session
     * cancels the work it started.
     */
    private suspend fun serve(session: PeerSession<FileServerMessages>) = coroutineScope {
        val scope = this
        val context = SessionContext()

        // Bounds concurrent long-running work per peer. Deliberately non-blocking: `:net` drops
        // fire-and-forget frames when `incoming` is not drained (PeerSessionImpl), so parking the
        // collector on a full semaphore loses upload chunks with nothing but a warning. A peer
        // that asks for more than this is told it was refused instead.
        val slots = Semaphore(MaxConcurrentRequests)

        session.incoming.collect { event ->
            if (event.message.isUploadStream()) {
                // Ordered on purpose: Init -> chunks -> UploadCompleted only mean anything in
                // arrival order, and dispatching them concurrently reorders them.
                handle(session, event, context)
                return@collect
            }

            if (!slots.tryAcquire()) {
                Timber.w("Refusing ${event.message::class.simpleName} from ${session.identity.deviceId}: $MaxConcurrentRequests requests already running")
                refuseBusy(event)
                return@collect
            }

            scope.launch {
                try {
                    handle(session, event, context)
                } finally {
                    slots.release()
                }
            }
        }
    }

    private suspend fun handle(
        session: PeerSession<FileServerMessages>,
        event: PeerSession.Inbound<FileServerMessages>,
        context: SessionContext,
    ) {
        try {
            dispatch(session, event, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to serve ${event.message}")
        }
    }

    private suspend fun dispatch(
        session: PeerSession<FileServerMessages>,
        event: PeerSession.Inbound<FileServerMessages>,
        context: SessionContext,
    ) {
        when (val message = event.message) {
            // `:net` matches answers to our own requests by correlation id before they ever reach
            // `incoming` (PeerSessionImpl.completeRequest), so one arriving here answers nothing.
            is FileServerMessages.Response ->
                Timber.w("Uncorrelated ${message::class.simpleName} from ${session.identity.deviceId}")

            is FileServerMessages.FetchFiles -> sendFiles(event, message, session)

            is FileServerMessages.UploadChunk -> {
                // TODO: read the chunk, append it to the file and record the locator on the
                //  matching SessionContext.UploadContext - UploadCompleted indexes what it finds there.
            }

            is FileServerMessages.OperationWithConfirmation -> {
                val result = runCatchingCancellable {
                    runOperation(session, message.instance, context)
                }

                val reply = event.reply
                if (reply == null) {
                    Timber.w("Cannot answer ${message.instance} from ${session.identity.deviceId}: no reply channel")
                    return
                }

                result.fold(
                    onSuccess = {
                        reply(FileServerMessages.Response.OperationCompleted(message.operationId))
                    },
                    onFailure = { t ->
                        Timber.w(
                            t,
                            "Operation ${message.instance} from ${session.identity.deviceId} failed"
                        )

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
            Timber.w("Cannot answer FetchFiles(${message.sourceId}) from ${session.identity.deviceId}: no reply channel")
            return
        }

        val files = runCatchingCancellable {
            val source = authorizedSource(session.identity, message.sourceId)
            localIndexer.refresh(source)
        }

        files.fold(
            onSuccess = { indexed ->
                reply(FileServerMessages.Response.FilesList(indexed.map { it.toDto() }))
            },
            onFailure = { t ->
                Timber.w(
                    t,
                    "Cannot list source ${message.sourceId} for ${session.identity.deviceId}"
                )

                // Answered rather than dropped: otherwise the peer waits out its request timeout.
                reply(
                    FileServerMessages.Response.FetchFilesFailed(
                        sourceId = message.sourceId,
                        reason = t.message ?: "Unknown error",
                    )
                )
            },
        )
    }

    /** Run a remote operation that the peer requested. */
    private suspend fun runOperation(
        session: PeerSession<FileServerMessages>,
        operation: RemoteOperation,
        context: SessionContext,
    ) {
        when (operation) {
            is RemoteOperation.File -> runFileOperation(session, operation)

            is RemoteOperation.Upload -> runUploadOperation(session, operation, context)
        }
    }

    private suspend fun runFileOperation(
        session: PeerSession<FileServerMessages>,
        operation: RemoteOperation.File,
    ) {
        val source = authorizedSource(session.identity, operation.key.sourceId)

        val file = storage.index.findFile(operation.key)
            ?: throw IllegalArgumentException("File ${operation.key.fileId} not found in source ${source.id}")

        when (operation) {
            is RemoteOperation.File.Hash -> localIndexer.hashFile(source, file)

            is RemoteOperation.File.Delete -> withContext(NonCancellable) {
                val fs = node.openSource(source.location.toFiles())

                // Thrown, not logged: the peer records this as done on our side once we confirm.
                if (!fs.deleteFile(file.locator)) {
                    throw IllegalStateException("Failed to delete ${file.path} from source ${source.id}")
                }

                storage.index.updateFileState(
                    key = operation.key,
                    state = IndexedFile.State.Deleted(deletedAt = timeProvider.now()),
                )
            }

            is RemoteOperation.File.Download -> {
                // Peer requests us to send them the file. It goes back over the session that asked
                // for it, which is not necessarily the one the source normally syncs over.
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
        context: SessionContext,
    ) {
        when (operation) {
            is RemoteOperation.Upload.Init -> {
                val source = authorizedSource(session.identity, operation.sourceId)
                val now = timeProvider.now()

                // Nothing else clears an upload whose sender stopped mid-stream.
                context.pruneStaleUploads(now)

                val key = IndexedFileKey(fileId = operation.file.id, sourceId = source.id)

                context.uploads[key] = SessionContext.UploadContext(
                    file = operation.file.toFileRecord(),
                    startedAt = now,
                )
            }

            is RemoteOperation.Upload.UploadCompleted -> {
                val source = authorizedSource(session.identity, operation.key.sourceId)

                val upload = context.uploads.remove(operation.key)
                    ?: throw IllegalStateException("No upload in progress for ${operation.key.fileId} in source ${source.id}")

                // Set by the chunk writer once bytes are on disk. Absent means we received none,
                // so there is nothing to point the index at - fail rather than record a phantom.
                val locator = upload.locator
                    ?: throw IllegalStateException("Received no bytes for ${operation.key.fileId} in source ${source.id}")

                val saved = storage.index.findFile(operation.key)

                val indexed = upload.file
                    .copy(
                        content = ContentHash(
                            value = operation.hash,
                            algorithm = operation.algorithm,
                        )
                    )
                    .toIndexed(
                        id = saved?.id ?: IdGenerator.nextId,
                        sourceId = source.id,
                        locator = locator,
                        currentTime = timeProvider.now(),
                    )

                storage.index.markProcessed(listOf(indexed))
            }
        }
    }

    /**
     * The source [sourceId] names, but only if [peer] is the device it syncs with.
     *
     * This is the whole access control on this side: every id served here was picked by the peer,
     * so without the check any authenticated device can list, delete or overwrite any source on
     * this one. "Not found" covers both misses on purpose - whether a source exists is not
     * something an unrelated peer gets to learn.
     */
    private suspend fun authorizedSource(peer: PeerIdentity, sourceId: String): SourceEntry {
        val source = storage.sources.findById(sourceId)

        if (source == null || source.deviceId != peer.deviceId) {
            if (source != null) {
                Timber.w("Device ${peer.deviceId} asked for source $sourceId, which syncs with ${source.deviceId}")
            }

            throw IllegalArgumentException("Source $sourceId not found")
        }

        return source
    }

    /** Tells the peer we are at capacity, so it fails now instead of waiting out its timeout. */
    private suspend fun refuseBusy(event: PeerSession.Inbound<FileServerMessages>) {
        val reply = event.reply ?: return

        when (val message = event.message) {
            is FileServerMessages.OperationWithConfirmation -> reply(
                FileServerMessages.Response.OperationFailed(
                    operationId = message.operationId,
                    reason = BusyReason,
                )
            )

            is FileServerMessages.FetchFiles -> reply(
                FileServerMessages.Response.FetchFilesFailed(
                    sourceId = message.sourceId,
                    reason = BusyReason,
                )
            )

            else -> Unit
        }
    }

    /** Messages that only mean anything in the order they were sent. */
    private fun FileServerMessages.isUploadStream(): Boolean = when (this) {
        is FileServerMessages.UploadChunk -> true
        is FileServerMessages.OperationWithConfirmation -> instance is RemoteOperation.Upload
        else -> false
    }

    companion object {
        private const val MaxConcurrentRequests = 5

        private const val BusyReason = "Receiver busy"
    }
}

private class ServedSession(
    val session: PeerSession<FileServerMessages>,
    val job: Job,
)

/** Per-session state. Owned by the coroutine serving that session, so a reconnect starts clean. */
private class SessionContext {
    val uploads: MutableMap<IndexedFileKey, UploadContext> = ConcurrentHashMap()

    fun pruneStaleUploads(now: Instant) {
        uploads.entries.removeAll { now - it.value.startedAt > UploadTimeout }
    }

    class UploadContext(
        val file: FileRecord,
        val startedAt: Instant,
    ) {
        /** Where the chunk writer put the bytes. Null until the first chunk lands. */
        @Volatile
        var locator: String? = null
    }

    companion object {
        private val UploadTimeout = 30.minutes
    }
}
