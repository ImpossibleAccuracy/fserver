package com.fserver.core.domain.repository

import com.fserver.core.domain.model.NetworkInfo
import kotlinx.coroutines.flow.Flow

interface NetworkInfoRepository {
    val networkInfo: Flow<NetworkInfo?>
}
