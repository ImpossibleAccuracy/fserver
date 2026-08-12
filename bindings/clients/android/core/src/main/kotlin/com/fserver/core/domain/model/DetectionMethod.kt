package com.fserver.core.domain.model

import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.spi.ip.DirectIpSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsSPI
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI

/**
 * A way to find a peer device.
 *
 * [Automatic] and [OnDemand] describe cost, not scheduling: automatic methods are quiet and cheap
 * enough to run together, on-demand ones are slow, loud, or need the user to supply something.
 * Neither starts on its own - every scan is begun by an explicit
 * [com.fserver.core.domain.repository.DevicesRepository.startDetection] call, because a
 * method that starts itself turns a permission the user was never asked for into "found nothing".
 *
 * @property [requires] is the entire availability rule: a method is offered only when the current
 * transport advertises every capability it names.
 * That keeps [availableDetectionMethods] free of per-network branching, and makes an unavailable
 * method a statement about physics ("mobile data carries no multicast") instead of a
 * hand-maintained list.
 */
sealed interface DetectionMethod {
    val spiId: SpiId
    val requires: Set<NetworkCapability>

    /**
     * Started as soon as discovery opens: quiet, cheap, safe to run unasked.
     */
    sealed interface Automatic : DetectionMethod {
        /**
         * Platform nearby-devices API. Runs over its own radios (BLE / Wi-Fi Direct), so it
         * needs nothing from the IP network and stays available with no connectivity at all.
         */
        data object NearbyConnections : Automatic {
            override val spiId: SpiId = NearbyConnectionsSPI.ID
            override val requires: Set<NetworkCapability> = emptySet()
        }

        /**
         * mDNS. Silently finds nothing wherever multicast is filtered - guest networks, AP
         * isolation - which is exactly why [OnDemand.SubnetScan] has to stay reachable as a fallback.
         */
        data object MulticastDns : Automatic {
            override val spiId: SpiId = MulticastDnsSPI.ID
            override val requires: Set<NetworkCapability> =
                setOf(NetworkCapability.LOCAL_SUBNET, NetworkCapability.MULTICAST)
        }
    }

    /** Slow or intrusive - never started without the user explicitly asking for it. */
    sealed interface OnDemand : DetectionMethod {

        /** Brute-force sweep of the local subnet. Slow and loud on the network. */
        data object SubnetScan : OnDemand {
            override val spiId: SpiId = DirectIpSPI.ID // TODO
            override val requires: Set<NetworkCapability> = setOf(NetworkCapability.LOCAL_SUBNET)
        }

        /** User types host:port. Works wherever there is a route at all. */
        data object ManualAddress : OnDemand {
            override val spiId: SpiId = DirectIpSPI.ID
            override val requires: Set<NetworkCapability> = setOf(NetworkCapability.IP_ROUTING)
        }
    }

    companion object {
        /** Every method, in the order the UI should consider them. */
        val entries: List<DetectionMethod> = listOf(
            Automatic.NearbyConnections,
            Automatic.MulticastDns,
            OnDemand.SubnetScan,
            OnDemand.ManualAddress,
        )
    }
}

val DetectionMethod.requiresArguments: Boolean
    get() = this is DetectionMethod.OnDemand.ManualAddress

/**
 * Detection methods usable on this transport.
 */
fun NetworkInfo?.availableDetectionMethods(): Set<DetectionMethod> {
    val capabilities = this?.capabilities.orEmpty()
    return DetectionMethod.entries.filterTo(LinkedHashSet()) { capabilities.containsAll(it.requires) }
}
