package com.fserver.app.di

import com.fserver.app.data.detection.scan.DeviceScannerFactory
import com.fserver.app.data.repository.DeviceDetectionRepositoryImpl
import com.fserver.app.data.repository.NetworkInfoRepositoryImpl
import com.fserver.app.domain.repository.DeviceDetectionRepository
import com.fserver.app.domain.repository.NetworkInfoRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule = module {
    singleOf(::DeviceScannerFactory)

    singleOf(::DeviceDetectionRepositoryImpl) bind DeviceDetectionRepository::class
    singleOf(::NetworkInfoRepositoryImpl) bind NetworkInfoRepository::class
}
