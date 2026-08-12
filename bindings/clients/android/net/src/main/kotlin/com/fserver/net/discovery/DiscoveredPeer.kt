package com.fserver.net.discovery

import com.fserver.net.connection.PeerRef
import com.fserver.net.peer.PeerDescriptor
import java.time.Instant

/**
 * A device as discovery sees it. One entry per device, however many methods found it - the
 * laptop that answers on both mDNS and Nearby is one row with two [routes].
 */
data class DiscoveredPeer(
    val descriptor: PeerDescriptor,
    val routes: List<PeerRef>,
    val lastSeen: Instant,
)
