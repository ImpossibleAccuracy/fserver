package com.fserver.net.session

import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.PeerIdentity
import com.fserver.net.spi.TransportId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * An open conversation with one device.
 *
 * The whole surface is closed over `M`: send a dictionary message, get a dictionary message,
 * answer with a dictionary message. There is deliberately no method that takes or returns a
 * `ByteArray` - bytes exist only inside
 * [com.fserver.net.dictionary.MessageCodec].
 *
 * The instance survives a dropped link: on failure the session goes back to
 * [SessionState.Connecting] and re-establishes itself, so a held reference stays valid. What it
 * does *not* do is re-send messages whose fate is unknown - see [SessionState].
 */
interface PeerSession<M : Any> {
    val peer: PeerIdentity

    /** Which transport currently carries this session; may change across a reconnect. */
    val transport: TransportId

    val state: StateFlow<SessionState>

    /**
     * Messages from the peer. Single-consumer: `:core` runs one handler over its own dictionary,
     * and a second collector would split the stream rather than duplicate it.
     */
    val incoming: Flow<Inbound<M>>

    /** Fire and forget. Succeeds once the frame reached the transport, not once the peer read it. */
    suspend fun send(message: M): Result<Unit>

    /** Sends and waits for the peer's answer, correlated by `:net` without reading either message. */
    suspend fun request(message: M, timeout: Duration? = null): Result<M>

    suspend fun close(reason: CloseReason = CloseReason.Normal)
}

/**
 * @property reply non-null when the peer is waiting for an answer. The answer is a message of the
 * same dictionary - `:net` only fills in the correlation id.
 */
data class Inbound<M : Any>(
    val message: M,
    val from: PeerIdentity,
    val reply: (suspend (M) -> Result<Unit>)?,
)

sealed interface SessionState {
    /** Opening, or re-opening after a lost link. */
    data object Connecting : SessionState

    data object Handshaking : SessionState

    data class Ready(val negotiated: NegotiatedParameters) : SessionState

    data class Closing(val reason: CloseReason) : SessionState

    data class Closed(val reason: CloseReason) : SessionState

    /** Terminal: the session could not be established or re-established. */
    data class Failed(val cause: Throwable) : SessionState
}

sealed interface CloseReason {
    data object Normal : CloseReason
    data object Idle : CloseReason
    data object RejectedByUser : CloseReason

    /** The peer closed, with whatever it said about why. */
    data class Remote(val detail: String) : CloseReason

    data class LinkLost(val cause: Throwable?) : CloseReason
    data class Protocol(val detail: String) : CloseReason
    data class Local(val detail: String) : CloseReason
}
