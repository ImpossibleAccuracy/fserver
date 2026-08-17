package com.fserver.net.connection

import com.fserver.net.peer.PublicGreeting

/**
 * A greeting and the route it came back over.
 *
 * The route is reported because a probe over a [com.fserver.net.discovery.DiscoveredPeer] tries
 * every route the peer advertises and stops at the first that answers - the caller has no other
 * way to learn which one that was.
 *
 * @property route [PeerRef.deviceId] is [PeerRef.UNKNOWN_DEVICE_ID] whenever the probe went to a
 * bare address: a greeting says nothing about which device answered, by design.
 */
data class ProbeResult(
    val route: PeerRef,
    val greeting: PublicGreeting,
)
