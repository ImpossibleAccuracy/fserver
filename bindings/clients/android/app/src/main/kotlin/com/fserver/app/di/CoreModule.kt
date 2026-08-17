package com.fserver.app.di

import com.fserver.core.FServerConfig
import com.fserver.core.FServerCore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Bridges `:core` into the app's Koin graph.
 *
 * `:core` runs its own private container, so this is the whole seam: build one [FServerCore] for
 * the process and republish the seams it exposes, so ViewModels keep injecting
 * `DeviceDetectionRepository` / `NetworkInfoRepository` / `RequirementsChecker` and never learn
 * where they came from.
 *
 * No `close()` call anywhere: the core lives as long as the process, and Android does not give
 * `Application` a reliable teardown callback to hang one on.
 */
val coreModule = module {
    single {
        FServerCore.create(
            FServerConfig(
                context = androidContext(),
                deviceIdentityStore = get(),
                authSettingsStore = get(),
            )
        )
    }

    single { get<FServerCore>().deviceDetection }
    single { get<FServerCore>().networkInfo }
    single { get<FServerCore>().requirements }
}
