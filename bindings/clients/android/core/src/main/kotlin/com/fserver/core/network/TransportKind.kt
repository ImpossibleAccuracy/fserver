package com.fserver.core.network

/**
 * A way to reach a peer device.
 *
 * [Automatic] describes cost, not scheduling: automatic kinds are quiet and cheap enough to run
 * together, the rest are slow, loud, or need the user to supply something. Neither starts on its
 * own - every scan is begun by an explicit
 * [com.fserver.core.network.device.DevicesRepository.startDetection] call, because a kind that
 * starts itself turns a permission the user was never asked for into "found nothing".
 */
sealed interface TransportKind {

    /**
     * Announces this device as well as finding others, so it is the only kind that can advertise.
     */
    sealed interface Automatic : TransportKind

    /**
     * Platform nearby-devices API. Runs over its own radios (BLE / Wi-Fi Direct), so it needs
     * nothing from the IP network and stays available with no connectivity at all.
     */
    data object NearbyConnections : Automatic

    /**
     * mDNS. Silently finds nothing wherever multicast is filtered - guest networks, AP isolation -
     * which is exactly why [SubnetScan] has to stay reachable as a fallback.
     */
    data object MulticastDns : Automatic

    /** Brute-force sweep of the local subnet. Slow and loud on the network. */
    data object SubnetScan : TransportKind

    /** User types host:port. Works wherever there is a route at all. */
    data object ManualAddress : TransportKind

    companion object {
        /** Every kind, in the order the UI should consider them. */
        val entries: List<TransportKind> = listOf(
            NearbyConnections,
            MulticastDns,
            SubnetScan,
            ManualAddress,
        )
    }
}
