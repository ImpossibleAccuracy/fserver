package com.fserver.net.session

import com.fserver.net.connection.PeerRef
import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.identity.PeerIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * An open conversation with one device.
 *
 * The whole surface is closed over `M`: send a dictionary message, get a dictionary message,
 * answer with a dictionary message. There is deliberately no method that takes or returns a
 * `ByteArray` - bytes exist only inside [com.fserver.net.dictionary.MessageCodec].
 *
 * The instance survives a dropped link: on failure the session goes back to [State.Connecting] and
 * re-establishes itself, so a held reference stays valid. What it does *not* do is re-send
 * messages whose fate is unknown - see [State].
 */
interface PeerSession<M : Any> {
    val route: PeerRef

    val descriptor: PeerDescriptor

    val state: StateFlow<State>

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

    /**
     * @property reply non-null when the peer is waiting for an answer. The answer is a message of
     * the same dictionary - `:net` only fills in the correlation id.
     */
    data class Inbound<T : Any>(
        val message: T,
        val from: PeerIdentity,
        val reply: (suspend (T) -> Result<Unit>)?,
    )

    sealed interface State {
        /** Opening, or re-opening after a lost link. */
        data object Connecting : State

        data object Handshaking : State

        data class Ready(val negotiated: NegotiatedParameters) : State

        data class Closing(val reason: CloseReason) : State

        data class Closed(val reason: CloseReason) : State

        /** Terminal: the session could not be established or re-established. */
        data class Failed(val cause: Throwable) : State
    }
}
