package com.fserver.core.di

import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.conflict.ConflictsController
import com.fserver.core.lifecycle.sync.AutoSyncCoordinator
import com.fserver.core.sync.device.DeviceConstraintChecker
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.lease.SyncLeaseRegistry
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.remote.IndexPublisher
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.sync.server.handler.FetchFilesHandler
import com.fserver.core.sync.server.handler.FileOperationHandler
import com.fserver.core.sync.server.handler.PublishIndexHandler
import com.fserver.core.sync.server.PeerRequestServer
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.sync.server.handler.SyncLeaseHandler
import com.fserver.core.sync.server.handler.upload.FileUploadHandler
import com.fserver.core.sync.server.handler.upload.UploadStaging
import com.fserver.core.sync.setup.SourceSetupExchange
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.core.sync.lease.SyncLeaseNegotiator
import com.fserver.core.sync.runner.FileActionRunner
import com.fserver.core.sync.runner.FileDownloader
import com.fserver.core.sync.runner.FileUploader
import com.fserver.core.sync.runner.SyncRunner
import com.fserver.core.sync.runner.UploadStrategySelector
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** The sync engine: the pass this device runs, and the half that answers the peer's. */
internal val syncModule = module {
    singleOf(::HybridLogicalClock)
    singleOf(::LocalChangesIndexer)
    singleOf(::UploadStrategySelector)
    singleOf(::DeviceConstraintChecker)
    singleOf(::SyncProgressReporter)

    singleOf(::PeerIndexFetcher)
    singleOf(::IndexPublisher)
    singleOf(::SourceSetupExchange)

    // The answering half: one handler per request family behind the listener.
    singleOf(::PeerRequestServer)
    singleOf(::SourceAuthorizer)
    singleOf(::FetchFilesHandler)
    singleOf(::PublishIndexHandler)
    singleOf(::SyncLeaseHandler)
    singleOf(::FileOperationHandler)
    singleOf(::FileUploadHandler)
    singleOf(::UploadStaging)

    singleOf(::FileUploader)
    singleOf(::FileDownloader)
    singleOf(::FileActionRunner)

    singleOf(::SyncLeaseRegistry)
    singleOf(::SyncLeaseNegotiator)

    singleOf(::SyncRunner)
    singleOf(::AutoSyncCoordinator)
    singleOf(::SourcesController)
    singleOf(::ConflictsController)
}
