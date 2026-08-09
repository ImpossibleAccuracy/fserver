package com.fserver.app.di

import com.fserver.app.data.di.dataSourceModule
import com.fserver.app.data.repository.DeviceDetectionRepositoryImpl
import com.fserver.app.data.repository.NearbyConnectionsRepository
import com.fserver.app.data.repository.NetworkInfoRepositoryImpl
import com.fserver.app.domain.repository.DeviceDetectionRepository
import com.fserver.app.domain.repository.NetworkInfoRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule = module {
    includes(dataSourceModule)

    singleOf(::DeviceDetectionRepositoryImpl) bind DeviceDetectionRepository::class
    singleOf(::NetworkInfoRepositoryImpl) bind NetworkInfoRepository::class

    // Data-only repositories
    singleOf(::NearbyConnectionsRepository)
}
