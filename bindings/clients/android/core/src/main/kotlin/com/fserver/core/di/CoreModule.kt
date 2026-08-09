package com.fserver.core.di

import com.fserver.core.data.di.dataSourceModule
import com.fserver.core.data.repository.DeviceDetectionRepositoryImpl
import com.fserver.core.data.repository.NearbyConnectionsRepository
import com.fserver.core.data.repository.NetworkInfoRepositoryImpl
import com.fserver.core.domain.repository.DeviceDetectionRepository
import com.fserver.core.domain.repository.NetworkInfoRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val coreModule = module {
    includes(dataSourceModule)

    singleOf(::DeviceDetectionRepositoryImpl) bind DeviceDetectionRepository::class
    singleOf(::NetworkInfoRepositoryImpl) bind NetworkInfoRepository::class

    // Data-only repositories
    singleOf(::NearbyConnectionsRepository)
}
