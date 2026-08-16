package com.fserver.core.domain.repository

import com.fserver.core.domain.model.network.NetworkInfo
import kotlinx.coroutines.flow.Flow

interface NetworkInfoRepository {
    /**
     * The network this device is on, or `null` when there is none.
     *
     * Naming it - SSID, BSSID - is location-gated on Android; see
     * [RequirementsChecker.forNetworkInfo] for what has to be granted before the name is real.
     */
    val networkInfo: Flow<NetworkInfo?>
}
