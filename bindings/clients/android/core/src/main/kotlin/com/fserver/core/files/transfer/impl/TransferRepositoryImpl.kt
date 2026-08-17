package com.fserver.core.files.transfer.impl

import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.transfer.IncomingTransfer
import com.fserver.core.files.transfer.TransferRepository
import com.fserver.core.network.NetworkController
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalAtomicApi::class)
internal class TransferRepositoryImpl(
    private val network: NetworkController,
    private val backgroundScope: BackgroundScope,
) : TransferRepository {
    private val isListening = AtomicBoolean(false)
    private val jobsLock = Mutex()
    private val jobs = ConcurrentHashMap<PeerIdentity, Job>()

    private val _incomingTransfer = MutableStateFlow<IncomingTransfer?>(null)
    override val incomingTransfer: StateFlow<IncomingTransfer?> = _incomingTransfer.asStateFlow()

    override fun listenForTransfers() {
        if (!isListening.compareAndSet(false, true)) return

        backgroundScope.launch {
            network.incomingConnections.sessions.collect { sessions ->
                for (session in sessions) {
                    val peer = session.identity

                    jobsLock.withLock {
                        jobs.getOrPut(peer) {
                            backgroundScope.launch {
                                listenIncomingConnection(session)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun listenIncomingConnection(session: PeerSession<FileServerMessages>) {
        val lock = Mutex()

        session.incoming.collect { event ->
            lock.withLock {
                when (val message = event.message) {
                    is FileServerMessages.Response -> {
                        // Ignore
                    }

                    is FileServerMessages.Request -> {
                        reactToRequest(event, message)
                    }
                }
            }
        }
    }

    private suspend fun reactToRequest(
        event: PeerSession.Inbound<FileServerMessages>,
        message: FileServerMessages.Request
    ) {
        when (message) {
            is FileServerMessages.Request.TransferRequest -> {
                val deferred = CompletableDeferred<Boolean>()

                val transfer = IncomingTransferImpl(
                    answer = deferred,
                    event = message,
                )

                _incomingTransfer.update { transfer }

                val answer = deferred.await()

                _incomingTransfer.compareAndSet(transfer, null)

                val response = if (answer) {
                    FileServerMessages.Response.ConfirmTransfer
                } else {
                    FileServerMessages.Response.RejectTransfer
                }

                try {
                    event.reply?.invoke(response)
                    Timber.d("Sent response to transfer request: $response")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to send response to transfer request")
                }
            }
        }
    }


    override suspend fun sendFiles(deviceId: String, filesCount: Int): Result<Boolean> {
        val session = network.incomingConnections.session(deviceId)
            ?: throw IllegalArgumentException("No session found for deviceId: $deviceId")

        return session
            .request(
                message = FileServerMessages.Request.TransferRequest(filesCount),
                timeout = 2.minutes,
            )
            .map {
                it is FileServerMessages.Response.ConfirmTransfer
            }
    }

    private class IncomingTransferImpl(
        private val answer: CompletableDeferred<Boolean>,
        private val event: FileServerMessages.Request.TransferRequest,
    ) : IncomingTransfer {
        override val filesCount: Int = event.filesCount

        override suspend fun accept() {
            answer.complete(true)
        }

        override suspend fun reject() {
            answer.complete(false)
        }
    }
}
