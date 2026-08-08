package com.fserver.app.data.di

import com.fserver.app.data.detection.connector.DeviceConnectorFactory
import com.fserver.app.data.detection.scan.DeviceScannerFactory
import com.fserver.app.data.detection.scan.multicast.MulticastDnsDiscoveryService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataSourceModule = module {
    singleOf(::DeviceScannerFactory)
    singleOf(::DeviceConnectorFactory)
    singleOf(::MulticastDnsDiscoveryService)
}
