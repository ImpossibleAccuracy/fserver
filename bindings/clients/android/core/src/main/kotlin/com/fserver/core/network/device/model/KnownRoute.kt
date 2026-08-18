package com.fserver.core.network.device.model

import com.fserver.core.network.TransportKind
import com.fserver.core.network.info.model.PeerLocator

/**
 * How a device was reached, written down so it outlives the process. Which device it belongs to is
 * the key it is stored under, not part of it.
 *
 * One route per [transport]: a laptop seen over both mDNS and Nearby is remembered twice, the same
 * way discovery reports it once with two routes.
 *
 * Routes that can never be opened again are kept too - [isDialable] says which is which. They are
 * still worth having: "last seen over Nearby" is something to show the user even when there is
 * nothing left to dial.
 */
sealed interface KnownRoute {
    /** The detection method this route belongs to, and the key it is stored under with the device. */
    val transport: TransportKind

    /**
     * False when this exact route cannot be opened a second time - an endpoint read off an
     * accepted socket, or one whose address only means anything for as long as it was handed out.
     */
    val isDialable: Boolean

    /** Displayable form. Never parsed back - see the store for the persisted shape. */
    val address: String

    /** null when [isDialable] is false: there is nothing here to dial again. */
    fun asPeerLocator(): PeerLocator?

    data class Ip(
        override val transport: TransportKind,
        val host: String,
        val port: Int?,
        override val isDialable: Boolean,
    ) : KnownRoute {
        override val address: String
            get() = when {
                port == null -> host
                // A bare IPv6 host already contains colons; brackets keep the port readable.
                ':' in host -> "[$host]:$port"
                else -> "$host:$port"
            }

        override fun asPeerLocator(): PeerLocator? =
            PeerLocator.Ip(host, port).takeIf { isDialable }
    }

    /**
     * A Nearby endpoint id. Never dialable once written down: the id is handed out per advertising
     * session, so reaching the device again means discovering it again.
     */
    data class Nearby(
        val endpointId: String,
    ) : KnownRoute {
        override val transport: TransportKind = TransportKind.NearbyConnections
        override val isDialable: Boolean = false
        override val address: String get() = endpointId

        override fun asPeerLocator(): PeerLocator? = null
    }
}
