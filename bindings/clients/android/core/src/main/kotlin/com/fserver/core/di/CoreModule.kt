package com.fserver.core.di

import android.content.Context
import com.fserver.core.files.FilesController
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.impl.DevicesRepositoryImpl
import com.fserver.core.network.device.impl.JsonQrCodeParser
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.impl.NetworkInfoRepositoryImpl
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.requirement.impl.RequirementsCheckerImpl
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.sync.remote.PeerRequestServer
import com.fserver.core.sync.runner.SyncRunner
import com.fserver.core.sync.runner.UploadStrategySelector
import com.fserver.core.util.DefaultTimeProvider
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Work that must outlive the screen. Bound from the host config. */
internal typealias BackgroundScope = CoroutineScope

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
    single { DefaultTimeProvider } bind TimeProvider::class

    factoryOf(::JsonQrCodeParser)

    singleOf(::FilesController)

    singleOf(::LocalChangesIndexer)
    singleOf(::PeerIndexFetcher)
    singleOf(::PeerRequestServer)
    singleOf(::UploadStrategySelector)
    singleOf(::SyncRunner)
    singleOf(::SourcesController)

    singleOf(::DevicesRepositoryImpl) bind DevicesRepository::class
    singleOf(::NetworkInfoRepositoryImpl) bind NetworkInfoRepository::class
    singleOf(::RequirementsCheckerImpl) bind RequirementsChecker::class
}
