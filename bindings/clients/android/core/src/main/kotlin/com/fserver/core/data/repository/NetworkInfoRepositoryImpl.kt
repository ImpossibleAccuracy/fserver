package com.fserver.core.data.repository

import com.fserver.core.domain.model.NetworkInfo
import com.fserver.core.domain.repository.NetworkInfoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NetworkInfoRepositoryImpl : NetworkInfoRepository {
    override val networkInfo: Flow<NetworkInfo?> = flowOf(
        NetworkInfo.WiFi(
            ssid = "Home_5G",
            bssid = "00:11:22:33:44:55",
        )
    )
}
