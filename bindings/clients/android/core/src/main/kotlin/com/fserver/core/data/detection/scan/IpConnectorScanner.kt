package com.fserver.core.data.detection.scan

import com.fserver.core.data.detection.link.IpDeviceLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class IpConnectorScanner(
    val ipAddress: String,
    val port: Int?,
) : DeviceScanner {
    override fun startScan(): Flow<DeviceScanEvent> = flowOf(
        DeviceScanEvent.Found(
            IpDeviceLink(
                ipAddress = ipAddress,
                port = port,
            )
        )
    )
}
