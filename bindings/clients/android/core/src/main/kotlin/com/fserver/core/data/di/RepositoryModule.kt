package com.fserver.core.data.di

import com.fserver.core.data.repository.DeviceDetectionRepositoryImpl
import com.fserver.core.data.repository.NearbyConnectionsRepository
import com.fserver.core.data.repository.NetworkInfoRepositoryImpl
import com.fserver.core.data.requirement.RequirementsCheckerImpl
import com.fserver.core.domain.repository.DeviceDetectionRepository
import com.fserver.core.domain.repository.NetworkInfoRepository
import com.fserver.core.domain.repository.RequirementsChecker
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule = module {
    singleOf(::DeviceDetectionRepositoryImpl) bind DeviceDetectionRepository::class
    singleOf(::NetworkInfoRepositoryImpl) bind NetworkInfoRepository::class
    singleOf(::RequirementsCheckerImpl) bind RequirementsChecker::class

    // Data-only repositories
    singleOf(::NearbyConnectionsRepository)
}
