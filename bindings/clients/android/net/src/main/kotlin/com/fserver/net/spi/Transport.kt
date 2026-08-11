package com.fserver.net.spi

import kotlinx.coroutines.flow.Flow

/** Opens outgoing channels, and - when it can - accepts incoming ones. */
interface Transport {
    val id: Id
    val capabilities: TransportCapabilities

    fun supports(endpoint: TransportEndpoint): Boolean

    suspend fun open(endpoint: TransportEndpoint): Result<Channel>

    /** null when the transport is outbound-only. */
    val listener: Listener?
        get() = null

    suspend fun shutdown() = Unit

    /** Identifies a transport implementation. Values are chosen by the implementing module. */
    @JvmInline
    value class Id(val value: String)

    /**
     * A duplex frame pipe. The transport must deliver whole frames, in order - a stream transport
     * does its own length-prefixing before it gets here.
     */
    interface Channel : AutoCloseable {
        val endpoint: TransportEndpoint

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
        val transport: Id
        val peer: DiscoveredEndpoint

        suspend fun accept(): Result<Channel>
        suspend fun reject()
    }
}

/**
 * What a transport can and cannot do. Read by `:net` when it picks a route and a cipher suite;
 * never used as a security control.
 */
data class TransportCapabilities(
    /** True when the link is already encrypted (Nearby). Does not remove the handshake. */
    val isLinkEncrypted: Boolean = false,
    val maxFrameSize: Int = DEFAULT_MAX_FRAME_SIZE,
    /** True when the transport has its own out-of-band confirmation, like Nearby's digits. */
    val requiresPeerConfirmation: Boolean = false,
    val isMetered: Boolean = false,
) {
    companion object {
        const val DEFAULT_MAX_FRAME_SIZE: Int = 512 * 1024
    }
}

/**
 * An address a [Transport] can open a channel to. Concrete types live in transport modules -
 * `:net` never inspects anything but [transport] and [address].
 */
interface TransportEndpoint {
    val transport: Transport.Id

    /** Stable textual form, used for logging and de-duplication only. */
    val address: String
}
