package com.fserver.net.discovery

import com.fserver.net.connection.PeerRef
import java.time.Instant

/**
 * A device as discovery sees it. One entry per device, however many methods found it - the
 * laptop that answers on both mDNS and Nearby is one row with two [routes].
 *
 * Everything in [advertised] is unverified by construction;
 * cconnection is what turns it into a [com.fserver.net.peer.PeerDescriptor].
 */
data class DiscoveredPeer(
    val advertised: AdvertisedPeer,
    val routes: List<PeerRef>,
    val lastSeen: Instant,
)
