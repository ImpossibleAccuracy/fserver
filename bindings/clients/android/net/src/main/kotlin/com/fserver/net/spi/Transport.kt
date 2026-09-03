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
 * What a transport can and cannot do. Declared statically,
 * before any connection exists - which is what makes properties peer-independent.
 */
data class TransportCapabilities(
    /**
     * Largest single frame this transport carries. Negotiated down to what both devices accept.
     *
     * A message larger than this is not refused - the session splits it across frames and the peer
     * rebuilds it, bounded by
     * [com.fserver.net.connection.SessionConfig.maxAssembledMessageSize].
     */
    val maxFrameSize: Int = DEFAULT_MAX_FRAME_SIZE,
    /** Transport-level security, if any. Null when the transport does not authenticate or encrypt. */
    val security: AuthMethodId? = null,
    /** Where the public greeting comes from. */
    val greeting: GreetingSource = GreetingSource.Wire,
) {
    init {
        require(maxFrameSize >= MIN_FRAME_SIZE) {
            "maxFrameSize $maxFrameSize is below the $MIN_FRAME_SIZE byte minimum"
        }
    }

    companion object {
        const val DEFAULT_MAX_FRAME_SIZE: Int = 512 * 1024

        /**
         * Smallest frame a session can work in.
         *
         * The handshake is what sets it: descriptors and auth rounds are single frames that cannot
         * be split, so transport below this could not finish a handshake, let alone carry
         * messages in useful pieces afterward.
         */
        const val MIN_FRAME_SIZE: Int = 4 * 1024
    }
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

    /** Host part of address, used for strong equality checks. */
    val hostAddress: String
        get() = address

    /**
     * False when the endpoint cannot be opened again - an endpoint built from an accepted socket
     * carries the peer's ephemeral source port, not the one it listens on.
     */
    val isDialable: Boolean
        get() = true
}
