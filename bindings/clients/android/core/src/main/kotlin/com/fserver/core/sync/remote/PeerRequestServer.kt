package com.fserver.core.sync.remote

import com.fserver.core.di.BackgroundScope
import com.fserver.core.network.NetworkController
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.store.FServerStorage
import com.fserver.files.FilesNode
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
    private val backgroundScope: BackgroundScope,
) {
    private val isListening = AtomicBoolean(false)
    private val jobsLock = Mutex()
    private val jobs = ConcurrentHashMap<PeerIdentity, Job>()

    /** Idempotent: repeated calls from the host keep the one listener already running. */
    fun start(): Job? {
        if (!isListening.compareAndSet(false, true)) return null

        return backgroundScope.launch {
            network.incomingConnections.sessions.collect { sessions ->
                for (session in sessions) {
                    val peer = session.identity

                    jobsLock.withLock {
                        jobs.getOrPut(peer) {
                            backgroundScope.launch {
                                try {
                                    serve(session)
                                } finally {
                                    jobs.remove(peer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun serve(session: PeerSession<FileServerMessages>) {
        session.incoming.collect { event ->
            try {
                dispatch(session, event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to serve ${event.message}")
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

            is FileServerMessages.OperationWithConfirmation -> {
                runOperation(message.instance)

                val reply = event.reply
                if (reply == null) {
                    Timber.w("Cannot reply to ${message.instance} from ${session.route.deviceId}: no reply channel")
                    return
                }

                reply(FileServerMessages.Response.OperationCompleted(message.operationId))
            }

            else -> {
                // TODO
            }
        }
    }

    /** Run a remote operation that the peer requested. */
    private suspend fun runOperation(operation: RemoteOperation) {
        TODO()
    }
}
