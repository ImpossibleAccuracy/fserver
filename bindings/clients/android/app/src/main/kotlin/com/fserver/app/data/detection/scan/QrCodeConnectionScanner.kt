package com.fserver.app.data.detection.scan

import com.fserver.app.data.datasource.JsonQrCodeParser
import com.fserver.app.data.detection.connector.IpDeviceConnector
import com.fserver.app.domain.model.exception.MalformedQrException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class QrCodeConnectionScanner(
    val payload: String
) : DeviceScanner {
    private val jsonQrCodeParser by lazy { JsonQrCodeParser() }

    override fun startScan(): Flow<DeviceScanEvent> = flow {
        val parsed = jsonQrCodeParser.parse(payload)
            ?: throw MalformedQrException()

        emit(
            DeviceScanEvent.Found(
                IpDeviceConnector(
                    ipAddress = parsed.ip,
                    port = parsed.port,
                )
            )
        )
    }
}
