package com.fserver.app.data.di

import com.fserver.app.data.datasource.multicastdns.MulticastDnsDiscoveryService
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsAdvertisingService
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsDiscoveryService
import com.fserver.app.data.detection.connector.DeviceConnectorFactory
import com.fserver.app.data.detection.scan.DeviceScannerFactory
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataSourceModule = module {
    factoryOf(::DeviceScannerFactory)
    factoryOf(::DeviceConnectorFactory)
    factoryOf(::MulticastDnsDiscoveryService)
    factoryOf(::NearbyConnectionsDiscoveryService)

    // Single, not factory: accept()/reject() must reach the same instance that is advertising,
    // otherwise the payload callback they register feeds a flow nobody collects.
    singleOf(::NearbyConnectionsAdvertisingService)
}
