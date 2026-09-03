package com.fserver.net.session

import com.fserver.common.exception.NetworkException
import com.fserver.net.NetLogger
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.ReconnectPolicy
import com.fserver.net.dictionary.MessageCodec
import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.session.PeerSession.Inbound
import com.fserver.net.session.PeerSession.State
import com.fserver.net.wire.Envelope
import com.fserver.net.wire.Fragment
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
    override val identity: PeerIdentity = negotiated.peer
    override val descriptor: PeerDescriptor = negotiated.peerDescriptor
    override val state: StateFlow<State> = _state.asStateFlow()

    override val maxPayloadSize: Int
        get() {
            val current = link.value
            val frameLimit = currentNegotiated().maxFrameSize
            // No link yet means no aead to ask: the budget is re-read per message, and the only
            // send that could see this one is the first after a reconnect.
            val sealing = current?.secure?.overhead ?: 0

            return (frameLimit - sealing - Envelope.Codec.HEADER_SIZE).coerceAtLeast(0)
        }

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

    // Keyed by the message id every slice of one message carries. Cleared with the link: half a
    // message is worth nothing once the sender is gone.
    private val assembling = ConcurrentHashMap<Long, Assembly>()
    private val nextAssembly = AtomicLong(1)

    // ------------------------------------------------------------------ lifecycle

    fun start(initial: SessionLink) {
        install(initial)
        scope.launch { writeLoop() }
        policy.timeouts.keepAlive?.let { period -> scope.launch { keepAliveLoop(period) } }
    }

    private fun install(newLink: SessionLink) {
        assembling.clear()
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
        assembling.clear()
        failPending(NetworkException.SessionClosed())
        link.value?.let { runCatching { it.secure.close() } }
        link.value = null
        _state.value = finalState
        controlQueue.close()
        appQueue.close()
        failQueued(controlQueue)
        failQueued(appQueue)
        incomingMessages.close()
        onTerminated(this)
        scope.cancel()
    }

    /** Frames that never made it onto the wire; their senders are still holding the ack. */
    private fun failQueued(queue: Channel<OutgoingFrame>) {
        while (true) {
            val frame = queue.tryReceive().getOrNull() ?: return
            frame.ack?.complete(Result.failure(NetworkException.SessionClosed()))
        }
    }

    // ------------------------------------------------------------------ public API

    override suspend fun send(message: M): Result<Unit> = guarded {
        submit(
            kind = FrameKind.MESSAGE,
            messageId = nextMessageId.getAndIncrement(),
            message = message,
        )
    }

    override suspend fun request(message: M, timeout: Duration?): Result<M> = guarded {
        val id = nextMessageId.getAndIncrement()
        val answer = CompletableDeferred<Result<M>>()
        pending[id] = answer

        try {
            submit(
                kind = FrameKind.REQUEST,
                messageId = id,
                message = message,
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

            try {
                // A frame taken while the link is down waits for the next one rather than being lost.
                val current = link.filterNotNull().first()
                val result = runCatching {
                    current.secure.send(Envelope.Codec.encode(frame.envelope)).getOrThrow()
                }

                frame.ack?.complete(result)
                if (result.isFailure) {
                    logger.warn(
                        "failed to send ${frame.envelope.kind} to ${negotiated.peer.deviceId}",
                        result.exceptionOrNull()
                    )
                }
            } catch (e: Throwable) {
                frame.ack?.complete(Result.failure(NetworkException.SessionClosed()))
                throw e
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

    /**
     * Never suspends: the reader is the only thing that can complete a pending request or answer a
     * keep-alive, so it must not be parked behind whoever consumes [incoming].
     */
    private fun dispatch(envelope: Envelope) {
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

            FrameKind.CHUNK -> assemble(envelope)?.let(::dispatch)

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

    /**
     * One slice in; the message it completes, or null while slices are still missing.
     *
     * Everything the peer says about a slice is checked before a byte of it is kept: the id it
     * belongs to is the peer's to choose, and so is how many slices it claims are coming.
     */
    private fun assemble(envelope: Envelope): Envelope? {
        val fragment = try {
            Fragment.Codec.decode(envelope.payload)
        } catch (e: NetworkException) {
            logger.warn("malformed chunk from ${negotiated.peer.deviceId}", e)
            return null
        }

        if (fragment.kind !in FRAGMENTABLE || fragment.total < 1 ||
            fragment.index !in 0 until fragment.total
        ) {
            logger.warn("chunk ${fragment.index}/${fragment.total} of ${fragment.kind} is not a message this session can rebuild")
            return null
        }

        val assembly = assembling[envelope.messageId] ?: run {
            evictOldestAssemblyIfFull()

            Assembly(
                kind = fragment.kind,
                correlationId = envelope.correlationId,
                total = fragment.total,
                sequence = nextAssembly.getAndIncrement(),
            ).also { assembling[envelope.messageId] = it }
        }

        // A slice that disagrees with the ones before it says the sender is confused; so is
        // anything already gathered under that id.
        if (assembly.kind != fragment.kind || assembly.total != fragment.total) {
            logger.warn("chunk of message ${envelope.messageId} contradicts its earlier slices")
            assembling.remove(envelope.messageId)
            return null
        }

        if (!assembly.accept(fragment, policy.sessionConfig.maxAssembledMessageSize)) {
            logger.warn("message ${envelope.messageId} from ${negotiated.peer.deviceId} exceeds the message limit - dropped")
            assembling.remove(envelope.messageId)
            return null
        }

        if (!assembly.isComplete) return null
        assembling.remove(envelope.messageId)

        return Envelope(
            version = envelope.version,
            kind = assembly.kind,
            messageId = envelope.messageId,
            correlationId = assembly.correlationId,
            payload = assembly.join(),
        )
    }

    /**
     * Makes room by dropping the assembly that has waited longest.
     *
     * The oldest goes rather than the newest being refused: a message whose rest never arrives -
     * the sender died, or a reconnect cut it in half - would otherwise hold its slot for as long
     * as the session lives and take the channel down with it a few stalls later.
     */
    private fun evictOldestAssemblyIfFull() {
        if (assembling.size < policy.sessionConfig.maxAssemblingMessages) return

        val oldest = assembling.minByOrNull { it.value.sequence } ?: return
        assembling.remove(oldest.key)
        logger.warn("dropping half-rebuilt message ${oldest.key} from ${negotiated.peer.deviceId} - its slices stopped coming")
    }

    private fun deliver(envelope: Envelope) {
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
                    submit(
                        kind = FrameKind.RESPONSE,
                        messageId = nextMessageId.getAndIncrement(),
                        correlationId = envelope.messageId,
                        message = answer,
                    )
                }
            }
        } else {
            null
        }

        if (incomingMessages.trySend(Inbound(message, reply)).isSuccess) return

        // The queue is full, so the host is not draining `incoming`.
        // Refusing the frame keeps the session honest.
        logger.warn("inbound queue full - refusing ${envelope.kind} from ${negotiated.peer.deviceId}")
        if (envelope.kind == FrameKind.REQUEST) {
            enqueueControl(
                control(
                    FrameKind.ERROR,
                    payload = "receiver busy".encodeToByteArray(),
                    correlationId = envelope.messageId,
                )
            )
        }
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

    /**
     * Queues [message], in one frame or in as many as it takes.
     *
     * Splitting happens here rather than above the session because only this layer knows what a
     * frame holds - the transport's limit, less the envelope and the seal. Callers hand over whole
     * messages and never see the seam.
     */
    private suspend fun submit(
        kind: FrameKind,
        messageId: Long,
        correlationId: Long = Envelope.NO_CORRELATION,
        message: M,
    ) {
        val payload = codec.encode(message)
        val budget = maxPayloadSize

        if (payload.size <= budget) {
            enqueue(
                queue = appQueue,
                envelope = Envelope(
                    version = ProtocolVersions.CURRENT,
                    kind = kind,
                    messageId = messageId,
                    correlationId = correlationId,
                    payload = payload,
                ),
            )
            return
        }

        submitFragmented(
            kind = kind,
            messageId = messageId,
            correlationId = correlationId,
            payload = payload,
            budget = budget
        )
    }

    /**
     * The message as [FrameKind.CHUNK] frames, in order, under one message id.
     *
     * Send completes when the last slice is on the wire: a caller that got a success has had
     * the whole message sent, the same promise an unsplit one makes. A slice that fails takes the
     * send down with it and leaves the peer holding a partial message, which it drops.
     *
     * Every peer is expected to rebuild these - [FrameKind.CHUNK] is part of the protocol rather
     * than an extension to negotiate, so one that does not understand it fails on the frame kind.
     */
    private suspend fun submitFragmented(
        kind: FrameKind,
        messageId: Long,
        correlationId: Long,
        payload: ByteArray,
        budget: Int,
    ) {
        val ceiling = policy.sessionConfig.maxAssembledMessageSize
        if (payload.size > ceiling) {
            throw NetworkException.FrameTooLarge(size = payload.size, limit = ceiling)
        }

        val sliceSize = budget - Fragment.Codec.HEADER_SIZE
        if (sliceSize <= 0) {
            throw NetworkException.FrameTooLarge(size = payload.size, limit = budget)
        }

        val total = (payload.size + sliceSize - 1) / sliceSize

        for (index in 0 until total) {
            val from = index * sliceSize
            val slice = payload.copyOfRange(from, minOf(from + sliceSize, payload.size))

            enqueue(
                queue = appQueue,
                envelope = Envelope(
                    version = ProtocolVersions.CURRENT,
                    kind = FrameKind.CHUNK,
                    messageId = messageId,
                    correlationId = correlationId,
                    payload = Fragment.Codec.encode(
                        Fragment(kind = kind, index = index, total = total, part = slice)
                    ),
                ),
            )
        }
    }

    private fun currentNegotiated(): NegotiatedParameters = link.value?.negotiated ?: negotiated

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

    /** Slices of one message, until the last of them turns up. */
    private class Assembly(
        val kind: FrameKind,
        val correlationId: Long,
        val total: Int,
        /** Arrival order, so the one that has waited longest is the one evicted. */
        val sequence: Long,
    ) {
        private val slices = arrayOfNulls<ByteArray>(total)
        private var gathered = 0
        private var bytes = 0

        val isComplete: Boolean get() = gathered == total

        /** False when the message would outgrow [ceiling] - the caller drops the whole assembly. */
        fun accept(fragment: Fragment, ceiling: Int): Boolean {
            // A resent slice is not an error; a second, different one under the same index is not
            // worth telling apart from it, and neither may grow what was already accounted for.
            if (slices[fragment.index] != null) return true

            val grown = bytes.toLong() + fragment.part.size
            if (grown > ceiling) return false

            slices[fragment.index] = fragment.part
            bytes = grown.toInt()
            gathered++
            return true
        }

        fun join(): ByteArray {
            val message = ByteArray(bytes)
            var offset = 0

            for (slice in slices) {
                val part = slice ?: continue
                part.copyInto(message, offset)
                offset += part.size
            }

            return message
        }
    }

    private class OutgoingFrame(
        val envelope: Envelope,
        val ack: CompletableDeferred<Result<Unit>>?,
    )

    private companion object {
        val CLOSE_FLUSH: Duration = 2.seconds
        const val IDLE_PERIODS = 3L

        /** Kinds a split message may be rebuilt as. Nothing control or handshake is ever split. */
        val FRAGMENTABLE = setOf(FrameKind.MESSAGE, FrameKind.REQUEST, FrameKind.RESPONSE)
    }
}
