package com.fserver.net.session

import com.fserver.net.NetLogger
import com.fserver.net.RequestTimeoutException
import com.fserver.net.SessionClosedException
import com.fserver.net.SessionLinkLostException
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.ReconnectPolicy
import com.fserver.net.dictionary.MessageCodec
import com.fserver.net.security.PeerIdentity
import com.fserver.net.spi.TransportId
import com.fserver.net.wire.Envelope
import com.fserver.net.wire.EnvelopeCodec
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    override val peer: PeerIdentity,
    override val transport: TransportId,
    private val codec: MessageCodec<M>,
    private val policy: ConnectionPolicy,
    private val logger: NetLogger,
    parentScope: CoroutineScope,
    /** null when this session cannot be rebuilt (an inbound one) - a lost link ends it. */
    private val relink: (suspend () -> SessionLink)?,
    private val onTerminated: (PeerSessionImpl<M>) -> Unit,
) : PeerSession<M> {

    private val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) +
                CoroutineName("fserver-session-${peer.deviceId}")
    )

    private val _state = MutableStateFlow<SessionState>(SessionState.Connecting)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val incomingMessages = Channel<Inbound<M>>(capacity = policy.incomingQueueCapacity)
    override val incoming: Flow<Inbound<M>> = incomingMessages.receiveAsFlow()

    // Control frames get their own queue and are always drained first: a keep-alive or a close
    // must not wait behind a file transfer. The split is by FrameKind - no payload is read.
    private val controlQueue = Channel<OutgoingFrame>(Channel.BUFFERED)
    private val appQueue = Channel<OutgoingFrame>(policy.sendQueueCapacity)

    private val link = MutableStateFlow<SessionLink?>(null)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<Result<M>>>()
    private val nextMessageId = AtomicLong(1)
    private val lastInboundAt = AtomicLong(System.nanoTime())
    private val terminated = AtomicBoolean(false)

    // ------------------------------------------------------------------ lifecycle

    fun start(initial: SessionLink) {
        install(initial)
        scope.launch { writeLoop() }
        policy.keepAlive?.let { period -> scope.launch { keepAliveLoop(period) } }
    }

    private fun install(newLink: SessionLink) {
        link.value = newLink
        lastInboundAt.set(System.nanoTime())
        _state.value = SessionState.Ready(newLink.negotiated)
        scope.launch { readLoop(newLink) }
    }

    override suspend fun close(reason: CloseReason) {
        if (!terminated.compareAndSet(false, true)) return
        _state.value = SessionState.Closing(reason)

        link.value?.let { current ->
            withTimeoutOrNull(CLOSE_FLUSH) {
                runCatching {
                    current.secure.send(
                        EnvelopeCodec.encode(control(FrameKind.CLOSE, describe(reason).encodeToByteArray()))
                    )
                }
            }
        }

        finish(SessionState.Closed(reason))
    }

    /** Ends the session without asking the peer - the link is already gone. */
    private fun terminate(finalState: SessionState) {
        if (!terminated.compareAndSet(false, true)) return
        finish(finalState)
    }

    private fun finish(finalState: SessionState) {
        failPending(SessionClosedException())
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
        enqueue(appQueue, app(FrameKind.MESSAGE, codec.encode(message))).getOrThrow()
    }

    override suspend fun request(message: M, timeout: Duration?): Result<M> = guarded {
        val id = nextMessageId.getAndIncrement()
        val answer = CompletableDeferred<Result<M>>()
        pending[id] = answer

        try {
            enqueue(appQueue, Envelope(
                version = ProtocolVersions.CURRENT,
                kind = FrameKind.REQUEST,
                messageId = id,
                payload = codec.encode(message),
            )).getOrThrow()

            val effectiveTimeout = timeout ?: policy.requestTimeout
            val result = withTimeoutOrNull(effectiveTimeout) { answer.await() }
                ?: throw RequestTimeoutException(effectiveTimeout)

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
            val result = runCatching { current.secure.send(EnvelopeCodec.encode(frame.envelope)).getOrThrow() }

            frame.ack?.complete(result)
            if (result.isFailure) {
                logger.warn("failed to write ${frame.envelope.kind}", result.exceptionOrNull())
            }
        }
    }

    private suspend fun enqueue(queue: Channel<OutgoingFrame>, envelope: Envelope): Result<Unit> {
        val ack = CompletableDeferred<Result<Unit>>()
        try {
            queue.send(OutgoingFrame(envelope, ack))
        } catch (_: ClosedSendChannelException) {
            throw SessionClosedException()
        }
        return ack.await()
    }

    private fun enqueueControl(envelope: Envelope) {
        controlQueue.trySend(OutgoingFrame(envelope, ack = null))
    }

    // ------------------------------------------------------------------ reading

    private suspend fun readLoop(current: SessionLink) {
        val failure = try {
            current.secure.inbound.collect { frame ->
                lastInboundAt.set(System.nanoTime())
                dispatch(EnvelopeCodec.decode(frame))
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
            FrameKind.PING -> enqueueControl(control(FrameKind.PONG, correlationId = envelope.messageId))

            FrameKind.PONG -> Unit

            FrameKind.CLOSE -> terminate(
                SessionState.Closed(CloseReason.Remote(envelope.payload.decodeToString()))
            )

            FrameKind.MESSAGE, FrameKind.REQUEST -> deliver(envelope)

            FrameKind.RESPONSE -> completeRequest(envelope)

            FrameKind.ERROR -> pending.remove(envelope.correlationId)?.complete(
                Result.failure(com.fserver.net.ProtocolException(envelope.payload.decodeToString()))
            )

            FrameKind.HELLO, FrameKind.HELLO_ACK, FrameKind.READY ->
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
                    ).getOrThrow()
                }
            }
        } else {
            null
        }

        incomingMessages.send(Inbound(message, peer, reply))
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
        failPending(SessionLinkLostException(cause))

        val rebuild = relink
        val backoff = policy.reconnect
        if (rebuild == null || backoff !is ReconnectPolicy.ExponentialBackoff) {
            terminate(SessionState.Closed(CloseReason.LinkLost(cause)))
            return
        }

        _state.value = SessionState.Connecting
        var delayMs = backoff.initialDelay.inWholeMilliseconds

        repeat(backoff.maxAttempts) { attempt ->
            delay(delayMs)
            val rebuilt = runCatching { rebuild() }
            rebuilt.getOrNull()?.let { install(it); return }

            logger.warn("reconnect attempt ${attempt + 1} failed", rebuilt.exceptionOrNull())
            delayMs = minOf(delayMs * 2, backoff.maxDelay.inWholeMilliseconds)
        }

        terminate(SessionState.Failed(SessionLinkLostException(cause)))
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
                logger.warn("peer ${peer.deviceId} silent for $IDLE_PERIODS keep-alive periods - dropping the link")
                // Closing the channel ends its inbound flow, which is what readLoop watches.
                runCatching { current.secure.close() }
                continue
            }

            enqueueControl(control(FrameKind.PING))
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun app(kind: FrameKind, payload: ByteArray) = Envelope(
        version = ProtocolVersions.CURRENT,
        kind = kind,
        messageId = nextMessageId.getAndIncrement(),
        payload = payload,
    )

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
        if (terminated.get()) throw SessionClosedException()
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
