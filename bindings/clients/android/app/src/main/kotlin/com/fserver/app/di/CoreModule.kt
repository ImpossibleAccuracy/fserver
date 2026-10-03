package com.fserver.app.di

import com.fserver.app.data.preview.CoilEvictionPreviewer
import com.fserver.core.FServerConfig
import com.fserver.core.FServerCore
import com.fserver.core.storage.FServerStorageProvider
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
    single {
        FServerConfig(
            context = get(),
            evictionPreviewer = get<CoilEvictionPreviewer>(),
        )
    }

    single { FServerStorageProvider.create(get()) }
    single {
        FServerCore.create(
            config = get(),
            storage = get<FServerStorageProvider>().asStorage(),
        )
    }

    single { get<FServerCore>().deviceDetection }
    single { get<FServerCore>().networkInfo }
    single { get<FServerCore>().lifecycle }
    single { get<FServerCore>().reachability }
    single { get<FServerCore>().requirements }
    single { get<FServerCore>().files }
    single { get<FServerCore>().encryption }
    single { get<FServerCore>().diskUsage }
    single { get<FServerCore>().sources }
    single { get<FServerCore>().conflicts }
    single { get<FServerCore>().pairingCodes }
    single { get<FServerCore>().oneShotTransfers }
    single { get<FServerCore>().export }
    single { get<FServerCore>().journal }
    single { get<FServerCore>().sources.progress }

    // Storage-backed repositories, republished so a ViewModel can read what the engine reads.
    single { get<FServerStorageProvider>().identity }
    single { get<FServerStorageProvider>().auth }
    single { get<FServerStorageProvider>().trustedDevices }
    single { get<FServerStorageProvider>().fileSources }
    single { get<FServerStorageProvider>().oneShotTransfers }
}
