package com.fserver.net.spi

import com.fserver.net.security.auth.AuthMethodId
import kotlinx.coroutines.flow.Flow

/** Opens outgoing channels, and - when it can - accepts incoming ones. */
interface Transport {
    val id: SpiId
    val capabilities: TransportCapabilities

    /** null when the transport is outbound-only. */
    val listener: Listener?
        get() = null

    fun supports(endpoint: TransportEndpoint): Boolean

    suspend fun open(endpoint: TransportEndpoint): Result<Channel>

    suspend fun shutdown()

    /**
     * A duplex frame pipe. The transport must deliver whole frames, in order - a stream transport
     * does its own length-prefixing before it gets here.
     */
    interface Channel : AutoCloseable {
        val endpoint: TransportEndpoint

        /**
         * Out-of-band code the transport produced while opening, for the dialling side - the same
         * role [DiscoveredEndpoint.confirmationCode] plays for the accepting one. Null when the
         * transport has none.
         */
        val confirmationCode: String?
            get() = null

        /** Completes when the link goes down; that is how `:net` learns the session lost its link. */
        val inbound: Flow<ByteArray>

        suspend fun send(frame: ByteArray): Result<Unit>
    }

    interface Listener {
        fun listen(): Flow<InboundConnection>
    }

    /**
     * An incoming connection that has not been answered yet. Nothing is accepted until the host
     * says so - see [InboundConnection.accept].
     */
    interface InboundConnection {
        val transport: SpiId
        val peer: DiscoveredEndpoint

        suspend fun accept(): Result<Channel>
        suspend fun reject()
    }
}

/**
 * What a transport can and cannot do. Declared statically, before any connection exists - which is
 * what makes [security] checkable: `:net` compares a peer's claim against its own transport's
 * declaration, never against the claim itself.
 */
data class TransportCapabilities(
    val maxFrameSize: Int = DEFAULT_MAX_FRAME_SIZE,
    val isMetered: Boolean = false,
    val security: ChannelSecurity = ChannelSecurity.None,
    val greeting: GreetingSource = GreetingSource.Wire,
) {
    companion object {
        const val DEFAULT_MAX_FRAME_SIZE: Int = 512 * 1024
    }
}

/** What the transport hands over before `:net` has done anything. */
sealed interface ChannelSecurity {
    /** Raw bytes. The full key agreement has to run. */
    data object None : ChannelSecurity

    /**
     * The transport encrypts the link itself and derives a short string from that key exchange,
     * which the user compares on both devices - a real SAS, not a placeholder.
     *
     * Only the method id lives here, because capabilities are static: the string itself belongs to
     * one connection and arrives through [Transport.Channel.confirmationCode] or
     * [DiscoveredEndpoint.confirmationCode].
     *
     * Note what this does *not* give: a long-term identity. The channel is authenticated, the
     * device is not, so a key still has to be exchanged and pinned inside it.
     */
    data class Sas(val method: AuthMethodId) : ChannelSecurity
}

/**
 * Where the public greeting comes from. [Transport] means the transport already exchanged it
 * out of band (Nearby carries it in the endpoint info, before the connection is accepted), so a
 * probe opens nothing at all.
 */
enum class GreetingSource { Wire, Transport, None }

/**
 * An address a [Transport] can open a channel to. Concrete types live in transport modules -
 * `:net` never inspects anything but [transport] and [address].
 */
interface TransportEndpoint {
    val transport: SpiId

    /** Stable textual form, used for logging and de-duplication only. */
    val address: String
}
