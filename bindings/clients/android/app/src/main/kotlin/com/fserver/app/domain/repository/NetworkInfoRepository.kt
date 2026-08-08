package com.fserver.app.domain.repository

import com.fserver.app.domain.model.NetworkInfo
import kotlinx.coroutines.flow.Flow

interface NetworkInfoRepository {
    val networkInfo: Flow<NetworkInfo?>
}
