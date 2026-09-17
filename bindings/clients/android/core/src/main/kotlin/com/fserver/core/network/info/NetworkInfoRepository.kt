package com.fserver.core.network.info

import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.requirement.RequirementsChecker
import kotlinx.coroutines.flow.Flow

interface NetworkInfoRepository {
    /**
     * The network this device is on, or `null` when there is none.
     *
     * Naming it - SSID, BSSID - is location-gated on Android; see
     * [RequirementsChecker.forNetworkInfo] for what has to be granted before the name is real.
     */
    val networkInfo: Flow<NetworkInfo?>

    /**
     * Where peers can reach this device right now - one route per listener address the running
     * transports report. Empty while nothing is serving, and re-read whenever [networkInfo]
     * changes, since an address outlives neither a link change nor a restart.
     */
    val localRoutes: Flow<List<KnownRoute>>

    /** Re-read the current network and re-emit it on [networkInfo]. */
    fun refresh()
}
