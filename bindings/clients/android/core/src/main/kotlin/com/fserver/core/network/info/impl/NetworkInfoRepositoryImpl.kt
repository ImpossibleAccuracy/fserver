package com.fserver.core.network.info.impl

import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class NetworkInfoRepositoryImpl : NetworkInfoRepository {
    override val networkInfo: Flow<NetworkInfo?> = flowOf(
        NetworkInfo.WiFi(
            ssid = "Home_5G",
            bssid = "00:11:22:33:44:55",
        )
    )
}
