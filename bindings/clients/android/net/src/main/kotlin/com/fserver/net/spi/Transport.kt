package com.fserver.net.spi

import kotlinx.coroutines.flow.Flow

/** Identifies a transport implementation. Values are chosen by the implementing module. */
@JvmInline
value class TransportId(val value: String)

/**
 * An address a [Transport] can open a channel to. Concrete types live in transport modules -
 * `:net` never inspects anything but [transport] and [address].
 */
interface TransportEndpoint {
    val transport: TransportId

    /** Stable textual form, used for logging and de-duplication only. */
    val address: String
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
 * A duplex frame pipe. The transport must deliver whole frames, in order - a stream transport
 * does its own length-prefixing before it gets here.
 */
interface TransportChannel : AutoCloseable {
    val endpoint: TransportEndpoint

    /** Completes when the link goes down; that is how `:net` learns the session lost its link. */
    val inbound: Flow<ByteArray>

    suspend fun send(frame: ByteArray): Result<Unit>
}

/** Opens outgoing channels, and - when it can - accepts incoming ones. */
interface Transport {
    val id: TransportId
    val capabilities: TransportCapabilities

    fun supports(endpoint: TransportEndpoint): Boolean

    suspend fun open(endpoint: TransportEndpoint): Result<TransportChannel>

    /** null when the transport is outbound-only. */
    val listener: TransportListener?
        get() = null

    suspend fun shutdown() = Unit
}

interface TransportListener {
    fun listen(): Flow<InboundConnection>
}

/**
 * An incoming connection that has not been answered yet. Nothing is accepted until the host says
 * so - see [InboundConnection.accept].
 */
interface InboundConnection {
    val transport: TransportId
    val peer: DiscoveredEndpoint

    suspend fun accept(): Result<TransportChannel>
    suspend fun reject()
}
