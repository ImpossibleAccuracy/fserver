package com.fserver.core.sync.server

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.network.NetworkController
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.impl.DevicesRepositoryImpl
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.sync.lease.SyncLeaseRegistry
import com.fserver.core.sync.server.handler.FetchFilesHandler
import com.fserver.core.sync.server.handler.FileOperationHandler
import com.fserver.core.sync.server.handler.PublishIndexHandler
import com.fserver.core.sync.server.handler.SyncLeaseHandler
import com.fserver.core.sync.server.handler.upload.FileUploadHandler
import com.fserver.core.sync.setup.SourceSetupExchange
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
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The answering half of sync: one long-lived listener that serves what peers ask of this device.
 *
 * `PeerIndexFetcher` and `FileActionRunner` drive a pass we started; every message arriving from a
 * pass the *peer* started lands here. Started by the host through `FServerCore.startServing`, and
 * lives as long as the engine - a request can arrive with no screen open.
 *
 * This class owns the session lifecycle and the dispatch table only. What each request family
 * means is one handler per file in this package, and every one of them resolves the source the
 * peer named through [SourceAuthorizer] rather than trusting the id.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class PeerRequestServer(
    private val network: NetworkController,
    private val leaseRegistry: SyncLeaseRegistry,
    private val sourceSetup: SourceSetupExchange,
    private val fetchFiles: FetchFilesHandler,
    private val publishedIndexes: PublishIndexHandler,
    private val leases: SyncLeaseHandler,
    private val fileOperations: FileOperationHandler,
    private val uploads: FileUploadHandler,
    private val devicesRepository: DevicesRepositoryImpl,
    private val requirementsChecker: RequirementsChecker,
    private val backgroundScope: BackgroundScope,
) {
    private val isListening = AtomicBoolean(false)

    private val jobsLock = Mutex()

    /** Guarded by [jobsLock]. Holds the session too, so a reconnect can be told from a re-emit. */
    private val jobs = HashMap<PeerIdentity, ServedSession>()

    private var listenerJob: Job? = null

    /**
     * Idempotent: repeated calls from the host keep the one listener already running.
     *
     * Refuses while the OS is withholding what a listener needs - no network at all, or the local
     * network permission API 37 puts in front of every LAN packet. A listener that binds anyway
     * hears nothing, and the host has no way to tell that from nobody calling.
     *
     * @return the listener's `Job`, `null` when it was already running, or the unmet report.
     */
    suspend fun start(): Result<Job?> {
        if (!isListening.compareAndSet(false, true)) return Result.success(null)

        // The listener serves the IP transports, which is what `ManualAddress` describes: any
        // route will do, and the local network permission is the one grant it asks for.
        val report = requirementsChecker.forTransport(TransportKind.ManualAddress)
        if (!report.isSatisfied) {
            isListening.store(false)
            return Result.failure(RequirementsNotMetException(report))
        }

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

        return Result.success(job)
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

            devicesRepository.recordReached(session)

            val job = backgroundScope.launch {
                try {
                    serve(session)
                } finally {
                    withContext(NonCancellable) {
                        // A peer that dropped mid-pass is not coming back to release what it held.
                        leaseRegistry.releaseAllFrom(peer.deviceId)

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
        val context = SessionContext(scope, uploads.sessionUploads(scope))

        // Bounds concurrent long-running work per peer. Deliberately non-blocking: `:net` drops
        // fire-and-forget frames when `incoming` is not drained (PeerSessionImpl), so parking the
        // collector on a full semaphore loses upload chunks with nothing but a warning. A peer
        // that asks for more than this is told it was refused instead.
        val slots = Semaphore(MaxConcurrentRequests)

        try {
            session.incoming.collect { event ->
                if (event.message.isOrderSensitive()) {
                    // Ordered on purpose: Init -> chunks -> Complete, and acquire -> release,
                    // only mean anything in arrival order - dispatching them concurrently reorders
                    // them, and a release overtaking its acquire strands the lease until it expires.
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
        } finally {
            // An upload the peer never finished waits in staging for it to come back.
            withContext(NonCancellable) { uploads.sessionEnded(context) }
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
            is FileServerMessages.Response ->
                Timber.w("Uncorrelated ${message::class.simpleName} from ${session.identity.deviceId}")

            is FileServerMessages.FetchFiles.Request ->
                fetchFiles.handle(event, message, session)

            is FileServerMessages.PublishIndex ->
                publishedIndexes.handle(message, session)

            is FileServerMessages.AcquireSyncLease.Request ->
                leases.answer(event, message, session)

            is FileServerMessages.AcquireSyncLease.ReleaseLease ->
                leases.release(message, session.identity)

            is FileServerMessages.ConfigureSource.Request ->
                sourceSetup.onRequest(session.identity, message)

            is FileServerMessages.ConfigureSource.Decision ->
                sourceSetup.onDecision(session.identity, message)

            is FileServerMessages.UploadChunk ->
                uploads.queueChunk(message, context)

            is FileServerMessages.Upload ->
                uploads.handle(event, message, session, context)

            is FileServerMessages.OperationWithConfirmation.Request ->
                answer(event, message, session)
        }
    }

    /** Runs the operation and confirms it, or says why it failed. */
    suspend fun answer(
        event: PeerSession.Inbound<FileServerMessages>,
        message: FileServerMessages.OperationWithConfirmation.Request,
        session: PeerSession<FileServerMessages>,
    ) {
        val result = runCatchingCancellable {
            when (val operation = message.instance) {
                is RemoteOperation.File -> fileOperations.handle(session, operation)
            }
        }

        val reply = event.reply
        if (reply == null) {
            Timber.w("Cannot answer ${message.instance} from ${session.identity.deviceId}: no reply channel")
            return
        }

        result.fold(
            onSuccess = {
                reply(FileServerMessages.OperationWithConfirmation.Completed(message.operationId))
            },
            onFailure = { t ->
                Timber.w(t, "Operation ${message.instance} from ${session.identity.deviceId} failed")

                reply(
                    FileServerMessages.OperationWithConfirmation.Failed(
                        operationId = message.operationId,
                        reason = t.message ?: "Unknown error"
                    )
                )
            }
        )
    }

    /** Tells the peer we are at capacity, so it fails now instead of waiting out its timeout. */
    private suspend fun refuseBusy(event: PeerSession.Inbound<FileServerMessages>) {
        val reply = event.reply ?: return

        when (val message = event.message) {
            is FileServerMessages.OperationWithConfirmation.Request -> reply(
                FileServerMessages.OperationWithConfirmation.Failed(
                    operationId = message.operationId,
                    reason = BusyReason,
                )
            )

            is FileServerMessages.FetchFiles.Request -> reply(
                FileServerMessages.FetchFiles.Failed(
                    sourceId = message.sourceId,
                    reason = BusyReason,
                )
            )

            else -> Unit
        }
    }

    /** Messages that only mean anything in the order they were sent. */
    private fun FileServerMessages.isOrderSensitive(): Boolean = when (this) {
        is FileServerMessages.UploadChunk -> true
        is FileServerMessages.Upload -> true
        is FileServerMessages.AcquireSyncLease.Request -> true
        is FileServerMessages.AcquireSyncLease.ReleaseLease -> true
        else -> false
    }

    private companion object {
        const val MaxConcurrentRequests = 5

        const val BusyReason = "Receiver busy"
    }
}

private class ServedSession(
    val session: PeerSession<FileServerMessages>,
    val job: Job,
)
