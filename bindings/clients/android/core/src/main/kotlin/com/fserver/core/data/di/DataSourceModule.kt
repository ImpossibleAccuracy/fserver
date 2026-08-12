package com.fserver.core.data.di

import com.fserver.core.data.datasource.JsonQrCodeParser
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** Work that must outlive the screen. Bound in [com.fserver.core.di.coreModule] from the host config. */
internal typealias BackgroundScope = CoroutineScope

/**
 * Datasource wiring. Pulled in by [com.fserver.core.di.coreModule], never installed on its own -
 * nothing here is part of the module's public surface.
 */
internal val dataSourceModule = module {
    factoryOf(::JsonQrCodeParser)
}
