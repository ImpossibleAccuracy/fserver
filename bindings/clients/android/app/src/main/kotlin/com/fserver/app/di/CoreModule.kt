package com.fserver.app.di

import com.fserver.core.FServerCore
import com.fserver.core.storage.FServerStorageProvider
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
 * Persistence comes from `:core:storage`, which is why nothing here implements a store. If a
 * screen needs something the engine persists, it injects a repository from
 * [FServerStorageProvider] - never a `com.fserver.core.store` type.
 *
 * No `close()` call anywhere: the core lives as long as the process, and Android does not give
 * `Application` a reliable teardown callback to hang one on.
 */
val coreModule = module {
    single { FServerStorageProvider.create(androidContext()) }

    single { FServerCore.create(get<FServerStorageProvider>().coreConfig()) }

    single { get<FServerCore>().deviceDetection }
    single { get<FServerCore>().networkInfo }
    single { get<FServerCore>().requirements }
    single { get<FServerCore>().transferRepository }
    single { get<FServerCore>().filesController }

    // Storage-backed repositories, republished so a ViewModel can read what the engine reads.
    single { get<FServerStorageProvider>().identity }
    single { get<FServerStorageProvider>().auth }
    single { get<FServerStorageProvider>().trustedDevices }
}
