package com.fserver.core.data.detection.scan

import com.fserver.core.data.detection.connector.IpDeviceConnector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class IpConnectorScanner(
    val ipAddress: String,
    val port: Int?,
) : DeviceScanner {
    override fun startScan(): Flow<DeviceScanEvent> = flowOf(
        DeviceScanEvent.Found(
            IpDeviceConnector(
                ipAddress = ipAddress,
                port = port,
            )
        )
    )
}
