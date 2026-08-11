package com.fserver.net.discovery

import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.SpiId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Finding devices and being findable.
 *
 * Nothing starts by itself: a scan that begins unasked turns a permission the user was never
 * shown into "found nothing".
 */
interface PeerDiscovery {
    /** Everything currently known, merged across providers. */
    val peers: StateFlow<List<DiscoveredPeer>>

    /** Active scans, by [SpiId]. */
    val activeScans: StateFlow<Set<SpiId>>

    /**
     * Runs the provider that [DiscoveryProvider.ScanParams] matches until it finishes, publishing
     * what it finds through [peers].
     *
     * @return what *this* run found - the address and code paths need the one device they asked
     * about and cannot pick it out of the accumulated list. Continuous providers (mDNS) only
     * return when the scan is stopped.
     */
    suspend fun scan(params: DiscoveryProvider.ScanParams): Result<List<DiscoveredPeer>>

    fun stopScan(id: SpiId)

    suspend fun startAdvertising(): Result<Unit>

    fun stopAdvertising()

    /** Listen for changes to the peer with the given [deviceId] */
    fun peer(deviceId: String): Flow<DiscoveredPeer?>
}
