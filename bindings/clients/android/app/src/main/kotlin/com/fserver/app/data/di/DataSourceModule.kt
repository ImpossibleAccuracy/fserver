package com.fserver.app.data.di

import com.fserver.app.data.datasource.multicastdns.MulticastDnsDiscoveryService
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsAdvertisingService
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsDiscoveryService
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsMessenger
import com.fserver.app.data.detection.connector.DeviceConnectorFactory
import com.fserver.app.data.detection.scan.DeviceScannerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

typealias BackgroundScope = CoroutineScope

val dataSourceModule = module {
    // Work that must outlive the screen
    single<BackgroundScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    factoryOf(::DeviceScannerFactory)
    factoryOf(::DeviceConnectorFactory)
    factoryOf(::MulticastDnsDiscoveryService)
    factoryOf(::NearbyConnectionsDiscoveryService)
    factoryOf(::NearbyConnectionsMessenger)

    // Single, not factory: accept()/reject() must reach the same instance that is advertising,
    // otherwise the payload callback they register feeds a flow nobody collects.
    singleOf(::NearbyConnectionsAdvertisingService)
}
