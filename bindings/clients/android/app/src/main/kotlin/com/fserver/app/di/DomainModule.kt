package com.fserver.app.di

import com.fserver.app.domain.AdvertisementLifecycleHandler
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val domainModule = module {
    singleOf(::AdvertisementLifecycleHandler)
}
