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

    /** Advertisers currently on the air, by [SpiId]. */
    val activeAdvertisers: StateFlow<Set<SpiId>>

    /**
     * Runs the provider that [DiscoveryProvider.ScanParams] matches until it finishes, publishing
     * what it finds through [peers].
     *
     * @return what *this* run found - the address and code paths need the one device they asked
     * about and cannot pick it out of the accumulated list. Continuous providers (mDNS) only
     * return when the scan is stopped.
     */
    suspend fun scan(params: DiscoveryProvider.ScanParams): Result<List<DiscoveredPeer>>

    /** Stop scan [id] if it is running, otherwise do nothing. */
    fun stopScan(id: SpiId)

    /**
     * Puts this device on the air over advertiser [id] and keeps it there across config reloads:
     * a reload that changes what would be advertised re-announces, one that changes nothing
     * leaves the advertiser alone.
     *
     * One advertiser per call, for the same reason a scan names its provider: several of them
     * need a runtime permission the user may never have been asked for, so announcing over
     * everything installed would fail on whatever the caller did not ask for.
     *
     * Advertising over an [id] already on the air does nothing.
     */
    suspend fun startAdvertising(id: SpiId): Result<Unit>

    /** Returns once advertiser [id] is actually off the air, not merely told to stop. */
    suspend fun stopAdvertising(id: SpiId)

    /** [stopAdvertising] for every advertiser currently on the air. */
    suspend fun stopAdvertising()

    /** Listen for changes to the peer with the given [deviceId] */
    fun peer(deviceId: String): Flow<DiscoveredPeer?>
}
