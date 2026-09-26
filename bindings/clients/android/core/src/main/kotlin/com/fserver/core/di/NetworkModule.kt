package com.fserver.core.di

import com.fserver.core.lifecycle.network.AutoAcceptCoordinator
import com.fserver.core.lifecycle.network.PresenceController
import com.fserver.core.network.NetworkController
import com.fserver.core.network.auth.PairingCodes
import com.fserver.core.network.auth.impl.InteractivePeerAuthenticator
import com.fserver.core.network.auth.impl.PairingCodesImpl
import com.fserver.core.network.device.DeviceReachability
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.impl.DevicesRepositoryImpl
import com.fserver.core.network.device.impl.ReachabilityTracker
import com.fserver.core.network.device.json.JsonQrCodeParser
import com.fserver.core.network.device.json.JsonQrCodeWriter
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.impl.NetworkInfoRepositoryImpl
import com.fserver.net.security.PeerAuthenticator
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Discovery, connections and the `:net` node behind them. */
internal val networkModule = module {
    singleOf(::NetworkController)
    single { lazy { get<NetworkController>() } }

    // Bound under both types: `:net` takes the interface, the devices repository drives the
    // concrete one to answer whoever is waiting on a confirmation.
    singleOf(::InteractivePeerAuthenticator) bind PeerAuthenticator::class

    // Bound under both types: the UI issues codes, the auth method spends them.
    single { PairingCodesImpl(timeProvider = get(), scope = get()) } bind PairingCodes::class

    factoryOf(::JsonQrCodeParser)
    factoryOf(::JsonQrCodeWriter)

    // Bound under both types: whoever dials writes to the tracker, everyone else reads the SPI.
    singleOf(::ReachabilityTracker) bind DeviceReachability::class

    singleOf(::DevicesRepositoryImpl) bind DevicesRepository::class
    singleOf(::NetworkInfoRepositoryImpl) bind NetworkInfoRepository::class

    singleOf(::PresenceController)
    singleOf(::AutoAcceptCoordinator)
}
