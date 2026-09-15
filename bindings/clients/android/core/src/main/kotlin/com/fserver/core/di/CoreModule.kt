package com.fserver.core.di

import com.fserver.core.FServerConfig
import com.fserver.core.lifecycle.LifecycleController
import com.fserver.core.store.FServerStorage
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Work that must outlive the screen. Bound from the host config. */
internal typealias BackgroundScope = CoroutineScope

/**
 * Wiring for the private container [com.fserver.core.FServerCore] owns.
 * Internal on purpose: consumers get the facade, never the graph - see that class for why.
 *
 * Holds only what the host hands in plus what every area shares; each area declares itself in its
 * own module, so a new class is registered next to its neighbours rather than in one list here.
 */
internal fun coreModule(
    config: FServerConfig,
    storage: FServerStorage,
): Module = module {
    includes(filesModule, networkModule, syncModule, requirementsModule)
    singleOf(::LifecycleController)

    // From the host. The whole config is bound too: `:net` reads context and storage off it.
    single { storage }
    single { config }
    single { config.context }
    single { config.timeProvider }
    single<BackgroundScope> { config.backgroundScope }
}
