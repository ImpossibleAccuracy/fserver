package com.fserver.core.di

import android.content.Context
import com.fserver.core.data.di.BackgroundScope
import com.fserver.core.data.di.dataSourceModule
import com.fserver.core.data.di.repositoryModule
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wiring for the private container [com.fserver.core.FServerCore] owns.
 * Internal on purpose: consumers get the facade, never the graph - see that class for why.
 *
 * [context] and [backgroundScope] come from the host through `FServerConfig`;
 * everything else `:core` builds itself.
 */
internal fun coreModule(
    context: Context,
    backgroundScope: CoroutineScope,
): Module = module {
    single { context }
    single<BackgroundScope> { backgroundScope }

    includes(dataSourceModule, repositoryModule)
}
