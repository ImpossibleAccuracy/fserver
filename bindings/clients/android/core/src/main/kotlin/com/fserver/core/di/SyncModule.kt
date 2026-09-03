package com.fserver.core.di

import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.device.DeviceConstraintChecker
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.lease.SyncLeaseRegistry
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.sync.server.handler.FetchFilesHandler
import com.fserver.core.sync.server.handler.FileOperationHandler
import com.fserver.core.sync.server.PeerRequestServer
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.sync.server.handler.SyncLeaseHandler
import com.fserver.core.sync.server.handler.upload.FileUploadHandler
import com.fserver.core.sync.setup.SourceSetupExchange
import com.fserver.core.sync.remote.SyncLeaseNegotiator
import com.fserver.core.sync.runner.FileActionRunner
import com.fserver.core.sync.runner.FileUploader
import com.fserver.core.sync.runner.SyncRunner
import com.fserver.core.sync.runner.UploadStrategySelector
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** The sync engine: the pass this device runs, and the half that answers the peer's. */
internal val syncModule = module {
    singleOf(::LocalChangesIndexer)
    singleOf(::UploadStrategySelector)
    singleOf(::DeviceConstraintChecker)

    singleOf(::PeerIndexFetcher)
    singleOf(::SourceSetupExchange)

    // The answering half: one handler per request family behind the listener.
    singleOf(::PeerRequestServer)
    singleOf(::SourceAuthorizer)
    singleOf(::FetchFilesHandler)
    singleOf(::SyncLeaseHandler)
    singleOf(::FileOperationHandler)
    singleOf(::FileUploadHandler)

    singleOf(::FileUploader)
    singleOf(::FileActionRunner)

    singleOf(::SyncLeaseRegistry)
    singleOf(::SyncLeaseNegotiator)

    singleOf(::SyncRunner)
    singleOf(::SourcesController)
}
