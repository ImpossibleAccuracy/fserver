package com.fserver.core.di

import com.fserver.core.network.NetworkController
import com.fserver.core.network.auth.impl.InteractivePeerAuthenticator
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.impl.DevicesRepositoryImpl
import com.fserver.core.network.device.impl.JsonQrCodeParser
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.impl.NetworkInfoRepositoryImpl
import com.fserver.core.network.presence.PresenceController
import com.fserver.net.security.PeerAuthenticator
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Discovery, connections and the `:net` node behind them. */
internal val networkModule = module {
    singleOf(::NetworkController)

    // Bound under both types: `:net` takes the interface, the devices repository drives the
    // concrete one to answer whoever is waiting on a confirmation.
    singleOf(::InteractivePeerAuthenticator) bind PeerAuthenticator::class

    factoryOf(::JsonQrCodeParser)

    singleOf(::DevicesRepositoryImpl) bind DevicesRepository::class
    singleOf(::NetworkInfoRepositoryImpl) bind NetworkInfoRepository::class

    singleOf(::PresenceController)
}
