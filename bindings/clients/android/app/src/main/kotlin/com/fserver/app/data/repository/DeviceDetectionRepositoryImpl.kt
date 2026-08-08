package com.fserver.app.data.repository

import com.fserver.app.domain.model.FoundDeviceDomain
import com.fserver.app.domain.repository.DeviceDetectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DeviceDetectionRepositoryImpl : DeviceDetectionRepository {
    override val onlineDevices: Flow<List<FoundDeviceDomain>> = flowOf(emptyList())
    override val isScanning: Flow<Boolean> = flowOf(false)

    override suspend fun startNetworkServiceDiscovery() {
    }

    override suspend fun startSubnetScan() {
    }

    override suspend fun pingDevice(ipAddress: String, port: Int?): Boolean {
        return false
    }

    override suspend fun startAndroidDiscovery() {
    }
}
