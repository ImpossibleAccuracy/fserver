package com.fserver.app.data.detection.scan

import com.fserver.app.data.detection.connector.DeviceConnector
import com.fserver.app.data.detection.connector.IpDeviceConnector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class IpConnectorScanner(
    val ipAddress: String,
    val port: Int?,
) : DeviceScanner {
    override suspend fun startScan(): Flow<DeviceConnector> = flowOf(
        IpDeviceConnector(
            ipAddress = ipAddress,
            port = port,
        )
    )
}
