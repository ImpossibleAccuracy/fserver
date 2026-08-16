package com.fserver.net.session

import com.fserver.net.NetLogger
import com.fserver.net.NetworkException
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.ReconnectPolicy
import com.fserver.net.dictionary.MessageCodec
import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.session.PeerSession.Inbound
import com.fserver.net.session.PeerSession.State
import com.fserver.net.wire.Envelope
import com.fserver.net.wire.FrameKind
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The session machine: two send queues, one reader per link, a correlation table, and a
 * reconnect loop.
 *
 * The dictionary codec is called in exactly two places here - encoding an outgoing payload and
 * decoding a `MESSAGE`/`REQUEST`/`RESPONSE` payload. Routing never touches it.
 */
internal class PeerSessionImpl<M : Any>(
    override val route: PeerRef,
    val negotiated: NegotiatedParameters,
    private val codec: MessageCodec<M>,
    private val policy: ConnectionPolicy,
    private val logger: NetLogger,
    parentScope: CoroutineScope,
    /** null when this session cannot be rebuilt (an inbound one) - a lost link ends it. */
    private val relink: (suspend () -> SessionLink)?,
    private val onTerminated: (PeerSessionImpl<M>) -> Unit,
) : PeerSession<M> {

    private val scope = CoroutineScope(
        parentScope.coroutineContext +
                SupervisorJob(parentScope.coroutineContext[Job]) +
                CoroutineName("net-session-${negotiated.peer.deviceId}")
    )

    private val _state = MutableStateFlow<State>(State.Connecting)
    override val descriptor: PeerDescriptor = negotiated.peerDescriptor
    override val state: StateFlow<State> = _state.asStateFlow()

    /**
     * What authenticated the link now in place, or the last one there was - a reconnecting session
     * is still a session that got in on that method, so a config reload judges it on that.
     */
    val authMethodId: AuthMethodId
        get() = ((_state.value as? State.Ready)?.negotiated ?: negotiated).authMethodId

    private val incomingMessages =
        Channel<Inbound<M>>(capacity = policy.sessionConfig.incomingQueueCapacity)
    override val incoming: Flow<Inbound<M>> = incomingMessages.receiveAsFlow()

    // Control frames get their own queue and are always drained first: a keep-alive or a close
    // must not wait behind a file transfer. The split is by Envelope.Kind - no payload is read.
    private val controlQueue = Channel<OutgoingFrame>(Channel.BUFFERED)
    private val appQueue = Channel<OutgoingFrame>(policy.sessionConfig.sendQueueCapacity)

    private val link = MutableStateFlow<SessionLink?>(null)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<Result<M>>>()
    private val nextMessageId = AtomicLong(1)
    private val lastInboundAt = AtomicLong(System.nanoTime())
    private val terminated = AtomicBoolean(false)

    // ------------------------------------------------------------------ lifecycle

    fun start(initial: SessionLink) {
        install(initial)
        scope.launch { writeLoop() }
        policy.timeouts.keepAlive?.let { period -> scope.launch { keepAliveLoop(period) } }
    }

    private fun install(newLink: SessionLink) {
        link.value = newLink
        lastInboundAt.set(System.nanoTime())
        _state.value = State.Ready(newLink.negotiated)
        scope.launch { readLoop(newLink) }
    }

    override suspend fun close(reason: CloseReason) {
        if (!terminated.compareAndSet(false, true)) return
        _state.value = State.Closing(reason)

        link.value?.let { current ->
            withTimeoutOrNull(CLOSE_FLUSH) {
                runCatching {
                    current.secure.send(
                        Envelope.Codec.encode(
                            control(
                                FrameKind.CLOSE,
                                describe(reason).encodeToByteArray()
                            )
                        )
                    )
                }
            }
        }

        finish(State.Closed(reason))
    }

    /** Ends the session without asking the peer - the link is already gone. */
    private fun terminate(finalState: State) {
        if (!terminated.compareAndSet(false, true)) return
        finish(finalState)
    }

    /** Post-termination cleanup. */
    private fun finish(finalState: State) {
        failPending(NetworkException.SessionClosed())
        link.value?.let { runCatching { it.secure.close() } }
        link.value = null
        _state.value = finalState
        controlQueue.close()
        appQueue.close()
        incomingMessages.close()
        onTerminated(this)
        scope.cancel()
    }

    // ------------------------------------------------------------------ public API

    override suspend fun send(message: M): Result<Unit> = guarded {
        enqueue(
            queue = appQueue,
            envelope = Envelope(
                version = ProtocolVersions.CURRENT,
                kind = FrameKind.MESSAGE,
                messageId = nextMessageId.getAndIncrement(),
                payload = codec.encode(message),
            )
        )
    }

    override suspend fun request(message: M, timeout: Duration?): Result<M> = guarded {
        val id = nextMessageId.getAndIncrement()
        val answer = CompletableDeferred<Result<M>>()
        pending[id] = answer

        try {
            enqueue(
                queue = appQueue,
                envelope = Envelope(
                    version = ProtocolVersions.CURRENT,
                    kind = FrameKind.REQUEST,
                    messageId = id,
                    payload = codec.encode(message),
                )
            )

            val effectiveTimeout = timeout ?: policy.timeouts.request
            val result = withTimeoutOrNull(effectiveTimeout) { answer.await() }
                ?: throw NetworkException.RequestTimeout(effectiveTimeout)

            result.getOrThrow()
        } finally {
            pending.remove(id)
        }
    }

    // ------------------------------------------------------------------ writing

    private suspend fun writeLoop() {
        while (currentCoroutineContext().isActive) {
            val frame = try {
                controlQueue.tryReceive().getOrNull() ?: select {
                    controlQueue.onReceive { it }
                    appQueue.onReceive { it }
                }
            } catch (_: ClosedReceiveChannelException) {
                return
            }

            // A frame taken while the link is down waits for the next one rather than being lost.
            val current = link.filterNotNull().first()
            val result = runCatching {
                current.secure.send(Envelope.Codec.encode(frame.envelope)).getOrThrow()
            }

            frame.ack?.complete(result)
            if (result.isFailure) {
                logger.warn("failed to write ${frame.envelope.kind}", result.exceptionOrNull())
            }
        }
    }

    private suspend fun enqueue(queue: Channel<OutgoingFrame>, envelope: Envelope) {
        val ack = CompletableDeferred<Result<Unit>>()
        try {
            queue.send(OutgoingFrame(envelope, ack))
        } catch (_: ClosedSendChannelException) {
            throw NetworkException.SessionClosed()
        }
        return ack.await().getOrThrow()
    }

    private fun enqueueControl(envelope: Envelope) {
        controlQueue.trySend(OutgoingFrame(envelope, ack = null))
    }

    // ------------------------------------------------------------------ reading

    private suspend fun readLoop(current: SessionLink) {
        val failure = try {
            current.secure.inbound.collect { frame ->
                lastInboundAt.set(System.nanoTime())
                dispatch(Envelope.Codec.decode(frame))
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e
        }

        if (terminated.get() || link.value !== current) return
        onLinkLost(failure)
    }

    private suspend fun dispatch(envelope: Envelope) {
        when (envelope.kind) {
            FrameKind.PING -> enqueueControl(
                control(
                    FrameKind.PONG,
                    correlationId = envelope.messageId
                )
            )

            FrameKind.PONG -> Unit

            FrameKind.CLOSE -> terminate(
                State.Closed(CloseReason.Remote(envelope.payload.decodeToString()))
            )

            FrameKind.MESSAGE, FrameKind.REQUEST -> deliver(envelope)

            FrameKind.RESPONSE -> completeRequest(envelope)

            FrameKind.ERROR -> pending.remove(envelope.correlationId)?.complete(
                Result.failure(NetworkException.Protocol(envelope.payload.decodeToString()))
            )

            FrameKind.HELLO, FrameKind.HELLO_ACK, FrameKind.AUTH,
            FrameKind.DESCRIPTOR, FrameKind.READY ->
                logger.warn("handshake frame ${envelope.kind} on an established session - ignored")
        }
    }

    private suspend fun deliver(envelope: Envelope) {
        val message = runCatching { codec.decode(envelope.payload) }.getOrElse { error ->
            logger.error("dictionary could not decode an incoming payload", error)
            if (envelope.kind == FrameKind.REQUEST) {
                enqueueControl(
                    control(
                        FrameKind.ERROR,
                        payload = "malformed payload".encodeToByteArray(),
                        correlationId = envelope.messageId,
                    )
                )
            }
            return
        }

        val reply: (suspend (M) -> Result<Unit>)? = if (envelope.kind == FrameKind.REQUEST) {
            { answer ->
                guarded {
                    enqueue(
                        appQueue,
                        Envelope(
                            version = ProtocolVersions.CURRENT,
                            kind = FrameKind.RESPONSE,
                            messageId = nextMessageId.getAndIncrement(),
                            correlationId = envelope.messageId,
                            payload = codec.encode(answer),
                        )
                    )
                }
            }
        } else {
            null
        }

        incomingMessages.send(Inbound(message, negotiated.peer, reply))
    }

    private fun completeRequest(envelope: Envelope) {
        val awaiting = pending.remove(envelope.correlationId) ?: run {
            logger.warn("response to unknown request ${envelope.correlationId}")
            return
        }
        awaiting.complete(runCatching { codec.decode(envelope.payload) })
    }

    // ------------------------------------------------------------------ link loss

    private suspend fun onLinkLost(cause: Throwable?) {
        link.value?.let { runCatching { it.secure.close() } }
        link.value = null
        failPending(NetworkException.SessionLinkLost(cause))

        val backoff = policy.reconnect
        if (relink == null || backoff == null) {
            terminate(State.Closed(CloseReason.LinkLost(cause)))
            return
        }

        _state.value = State.Connecting

        var delayMs = when (backoff) {
            is ReconnectPolicy.ExponentialBackoff -> backoff.initialDelay
            is ReconnectPolicy.StaticDelay -> backoff.delay
        }

        repeat(backoff.maxAttempts) { attempt ->
            delay(delayMs)
            val rebuilt = runCatching { relink() }
            rebuilt.getOrNull()?.let {
                install(it)
                return
            }

            logger.warn("reconnect attempt ${attempt + 1} failed", rebuilt.exceptionOrNull())

            val maxDelay = when (backoff) {
                is ReconnectPolicy.ExponentialBackoff -> backoff.maxDelay
                is ReconnectPolicy.StaticDelay -> backoff.delay
            }
            delayMs = minOf(delayMs * 2, maxDelay)
        }

        terminate(State.Failed(NetworkException.SessionLinkLost(cause)))
    }

    private fun failPending(cause: Throwable) {
        pending.keys.toList().forEach { id ->
            pending.remove(id)?.complete(Result.failure(cause))
        }
    }

    // ------------------------------------------------------------------ keep-alive

    private suspend fun keepAliveLoop(period: Duration) {
        val idleLimit = period.inWholeNanoseconds * IDLE_PERIODS

        while (currentCoroutineContext().isActive) {
            delay(period)
            val current = link.value ?: continue

            if (System.nanoTime() - lastInboundAt.get() > idleLimit) {
                logger.warn("peer ${negotiated.peer.deviceId} silent for $IDLE_PERIODS keep-alive periods - dropping the link")
                // Closing the channel ends its inbound flow, which is what readLoop watches.
                runCatching { current.secure.close() }
                continue
            }

            enqueueControl(control(FrameKind.PING))
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun control(
        kind: FrameKind,
        payload: ByteArray = Envelope.EMPTY,
        correlationId: Long = Envelope.NO_CORRELATION,
    ) = Envelope(
        version = ProtocolVersions.CURRENT,
        kind = kind,
        messageId = nextMessageId.getAndIncrement(),
        correlationId = correlationId,
        payload = payload,
    )

    /** Result-wrapping that still lets structured cancellation through. */
    private inline fun <T> guarded(block: () -> T): Result<T> = try {
        if (terminated.get()) throw NetworkException.SessionClosed()
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    private fun describe(reason: CloseReason): String = when (reason) {
        CloseReason.Normal -> "normal"
        CloseReason.Idle -> "idle"
        CloseReason.RejectedByUser -> "rejected"
        is CloseReason.Remote -> reason.detail
        is CloseReason.LinkLost -> "link lost"
        is CloseReason.Protocol -> reason.detail
        is CloseReason.Local -> reason.detail
    }

    private class OutgoingFrame(
        val envelope: Envelope,
        val ack: CompletableDeferred<Result<Unit>>?,
    )

    private companion object {
        val CLOSE_FLUSH: Duration = 2.seconds
        const val IDLE_PERIODS = 3L
    }
}
