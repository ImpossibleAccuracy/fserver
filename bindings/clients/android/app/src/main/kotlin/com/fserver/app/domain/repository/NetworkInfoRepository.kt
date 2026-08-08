package com.fserver.app.domain.repository

import com.fserver.app.domain.model.NetworkInfoDomain
import kotlinx.coroutines.flow.Flow

interface NetworkInfoRepository {
    val networkInfo: Flow<NetworkInfoDomain?>
}
