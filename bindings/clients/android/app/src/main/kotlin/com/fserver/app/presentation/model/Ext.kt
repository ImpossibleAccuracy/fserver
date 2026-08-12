package com.fserver.app.presentation.model

import com.fserver.core.domain.model.DetectionMethod
import com.fserver.net.discovery.DiscoveredPeer


/**
 * How to name this device's location to the user. Not every source has an IP — a nearby
 * peer is reached over its own radios — so this is the address in the loose sense: whatever
 * identifies the far end on the transport that found it.
 */
val DiscoveredPeer.address: String
    // TODO
    get() = "TODO" /*when (val source = source) {
        is DiscoveredPeer.Source.ManualEntry -> "${source.ipAddress}:${source.port}"
        is DiscoveredPeer.Source.SubnetScan -> "${source.ipAddress}:${source.port}"
        is DiscoveredPeer.Source.NearbyDevice -> source.deviceId
        is DiscoveredPeer.Source.NetworkServiceDiscovery ->
            "${source.serviceName}.${source.serviceType}.${source.ipAddress}"
    }*/

/**
 * Which method turned this device up. The source a device carries is the method's own output,
 * so the search screen can attribute every result without the engine counting for it.
 */
val DiscoveredPeer.foundBy: DetectionMethod
    // TODO
    get() = DetectionMethod.Automatic.MulticastDns /*when (source) {
        is DiscoveredPeer.Source.NetworkServiceDiscovery -> DetectionMethod.Automatic.MulticastDns
        is DiscoveredPeer.Source.NearbyDevice -> DetectionMethod.Automatic.NearbyConnections
        is DiscoveredPeer.Source.SubnetScan -> DetectionMethod.OnDemand.SubnetScan
        is DiscoveredPeer.Source.ManualEntry -> DetectionMethod.OnDemand.ManualAddress
    }*/
