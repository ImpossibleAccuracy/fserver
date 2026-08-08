package com.fserver.app.presentation.model

import com.fserver.app.domain.model.FoundDevice


/**
 * How to name this device's location to the user. Not every source has an IP — a nearby
 * peer is reached over its own radios — so this is the address in the loose sense: whatever
 * identifies the far end on the transport that found it.
 */
val FoundDevice.address: String
    get() = when (val source = source) {
        is FoundDevice.Source.ManualEntry -> "${source.ipAddress}:${source.port}"
        is FoundDevice.Source.SubnetScan -> "${source.ipAddress}:${source.port}"
        is FoundDevice.Source.NearbyDevice -> source.deviceId
        is FoundDevice.Source.NetworkServiceDiscovery ->
            "${source.serviceName}.${source.serviceType}.${source.ipAddress}"
    }
