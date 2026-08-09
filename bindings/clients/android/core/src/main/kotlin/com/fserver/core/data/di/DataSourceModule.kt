package com.fserver.core.data.di

import com.fserver.core.data.datasource.multicastdns.MulticastDnsDiscoveryService
import com.fserver.core.data.datasource.nearbyconnection.NearbyConnectionsAdvertisingService
import com.fserver.core.data.datasource.nearbyconnection.NearbyConnectionsDiscoveryService
import com.fserver.core.data.datasource.nearbyconnection.NearbyConnectionsMessenger
import com.fserver.core.data.detection.connector.DeviceConnectorFactory
import com.fserver.core.data.detection.scan.DeviceScannerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal typealias BackgroundScope = CoroutineScope

/**
 * Datasource wiring. Pulled in by [com.fserver.core.di.coreModule], never installed on its own —
 * nothing here is part of the module's public surface.
 */
internal val dataSourceModule = module {
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
