package com.fserver.core.network.device

import com.fserver.core.network.TransportKind

/**
 * A device asking to connect, parked on this device's answer.
 *
 * Nothing here is proven: the request arrives before authentication, so the name is whatever the
 * peer advertised and [isSecured] - when the transport produced one - is all there is to
 * compare against.
 */
interface IncomingConnection {
    /** The name the peer advertised for itself. Unverified. */
    val deviceName: String

    /** Transport kind used to establish the connection. */
    val transport: TransportKind?

    /** Whether the transport produced a secure channel. */
    val isSecured: Boolean

    suspend fun accept(): Result<Unit>

    suspend fun reject()
}
