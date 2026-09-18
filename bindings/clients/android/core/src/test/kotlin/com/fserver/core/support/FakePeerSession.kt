package com.fserver.core.support

import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.net.connection.PeerRef
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.time.Duration

/**
 * A session with no link behind it: everything sent is recorded, everything asked is answered by
 * [responder]. What the peer says is pushed in with [deliver].
 */
internal class FakePeerSession(
    override val identity: PeerIdentity = peerIdentity("device-peer"),
    override val maxPayloadSize: Int = 64 * 1024,
    /** The route the session came up over - what a caller writes down afterwards. */
    override val route: PeerRef = PeerRef.build(DirectIpEndpoint(host = "127.0.0.1", port = 1)),
    private val responder: (FileServerMessages) -> FileServerMessages? = { null },
) : PeerSession<FileServerMessages> {

    val sent: MutableList<FileServerMessages> = mutableListOf()
    val requested: MutableList<FileServerMessages> = mutableListOf()
    var closedWith: CloseReason? = null
        private set

    private val inboundChannel = Channel<PeerSession.Inbound<FileServerMessages>>(Channel.UNLIMITED)

    override val descriptor: PeerDescriptor = PeerDescriptor(
        displayName = "Fake peer",
        kind = null,
        dictionary = MessageDictionary.Descriptor(id = "fserver", version = 1),
        maxFrameSize = maxPayloadSize,
    )

    private val mutableState =
        MutableStateFlow<PeerSession.State>(PeerSession.State.Connecting)

    override val state: StateFlow<PeerSession.State> = mutableState

    override val incoming: Flow<PeerSession.Inbound<FileServerMessages>> =
        inboundChannel.consumeAsFlow()

    override suspend fun send(message: FileServerMessages): Result<Unit> {
        sent += message
        return Result.success(Unit)
    }

    override suspend fun request(message: FileServerMessages, timeout: Duration?): Result<FileServerMessages> {
        requested += message
        return responder(message)
            ?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("No canned answer for $message"))
    }

    override suspend fun close(reason: CloseReason) {
        closedWith = reason
        inboundChannel.close()
    }

    /** The link is up: only here is the session a device anything can be asked of. */
    fun markReady() {
        mutableState.value = PeerSession.State.Ready(
            NegotiatedParameters(
                protocolVersion = 1,
                dictionaryVersion = 1,
                cipherSuite = CryptoProvider.Suite(name = "test", isEncrypting = true),
                maxFrameSize = maxPayloadSize,
                peer = identity,
                peerDescriptor = descriptor,
                authMethodId = AuthMethodId("test"),
            )
        )
    }

    /** The link dropped. The session is still registered and reaches nobody while it rebuilds. */
    fun markConnecting() {
        mutableState.value = PeerSession.State.Connecting
    }

    /** Hands the server one message as if the peer had sent it. */
    suspend fun deliver(
        message: FileServerMessages,
        reply: (suspend (FileServerMessages) -> Result<Unit>)? = null,
    ) {
        inboundChannel.send(PeerSession.Inbound(message, reply))
    }

    /** Collects every answer the handler writes back into the request's reply channel. */
    class Replies {
        val messages: MutableList<FileServerMessages> = mutableListOf()

        val channel: suspend (FileServerMessages) -> Result<Unit> = {
            messages += it
            Result.success(Unit)
        }

        inline fun <reified T : FileServerMessages> only(): T {
            check(messages.size == 1) { "expected exactly one reply, got $messages" }
            return messages.single() as T
        }
    }
}
