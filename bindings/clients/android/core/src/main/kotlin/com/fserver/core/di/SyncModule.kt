package com.fserver.core.di

import android.content.Context
import com.fserver.core.files.StorageVolumes
import com.fserver.core.FServerConfig
import com.fserver.core.lifecycle.sync.AutoSyncCoordinator
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.conflict.ConflictCopier
import com.fserver.core.sync.conflict.ConflictResolver
import com.fserver.core.sync.conflict.ConflictsController
import com.fserver.core.sync.device.DeviceConstraintChecker
import com.fserver.core.sync.fileops.FileDeleter
import com.fserver.core.sync.fileops.FileEvictor
import com.fserver.core.sync.fileops.FileMover
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.LocalFileHasher
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.index.LocalVersions
import com.fserver.core.sync.index.SourceIndexLocks
import com.fserver.core.sync.lease.SyncLeaseNegotiator
import com.fserver.core.sync.lease.SyncLeaseRegistry
import com.fserver.core.sync.lease.SyncModeReconciler
import com.fserver.core.sync.metadata.PeerMetadataExchange
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.remote.IndexPublisher
import com.fserver.core.sync.remote.PeerConnector
import com.fserver.core.sync.remote.PeerFileOperations
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.sync.runner.SyncRunner
import com.fserver.core.sync.runner.UploadStrategySelector
import com.fserver.core.sync.runner.action.ActionSteps
import com.fserver.core.sync.runner.action.FileActionRunner
import com.fserver.core.sync.runner.pass.PassCompletion
import com.fserver.core.sync.runner.pass.SourcePassExecutor
import com.fserver.core.sync.server.PeerRequestServer
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.sync.server.handler.FetchFilesHandler
import com.fserver.core.sync.server.handler.FileOperationHandler
import com.fserver.core.sync.server.handler.PublishIndexHandler
import com.fserver.core.sync.server.handler.SyncLeaseHandler
import com.fserver.core.sync.server.handler.upload.FileUploadHandler
import com.fserver.core.sync.server.handler.upload.UploadAdmission
import com.fserver.core.sync.server.handler.upload.UploadStaging
import com.fserver.core.sync.setup.SourceSetupExchange
import com.fserver.core.sync.transfer.FileDownloader
import com.fserver.core.sync.transfer.FileUploader
import com.fserver.core.sync.transfer.RequestedDownloads
import com.fserver.core.sync.version.HybridLogicalClock
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** The sync engine: the pass this device runs, and the half that answers the peer's. */
internal val syncModule = module {
    singleOf(::HybridLogicalClock)
    singleOf(::UploadStrategySelector)
    singleOf(::DeviceConstraintChecker)
    singleOf(::SyncProgressReporter)

    // The local index: scans, and every other write under the same per-source lock.
    singleOf(::SourceIndexLocks)
    singleOf(::LocalVersions)
    singleOf(::LocalIndexWriter)
    singleOf(::LocalFileHasher)
    singleOf(::LocalChangesIndexer)

    singleOf(::PeerConnector)
    singleOf(::PeerIndexFetcher)
    singleOf(::PeerFileOperations)
    singleOf(::IndexPublisher)
    singleOf(::SourceSetupExchange)
    single {
        val context = get<Context>()
        PeerMetadataExchange(get(), get()) { StorageVolumes.fromContext(context).volumes }
    }

    // The answering half: one handler per request family behind the listener.
    singleOf(::PeerRequestServer)
    singleOf(::SourceAuthorizer)
    singleOf(::FetchFilesHandler)
    singleOf(::PublishIndexHandler)
    singleOf(::SyncLeaseHandler)
    singleOf(::FileOperationHandler)
    singleOf(::FileUploadHandler)
    singleOf(::UploadAdmission)
    singleOf(::UploadStaging)

    // Moving bytes, and changing files in place, for either half.
    singleOf(::FileUploader)
    singleOf(::FileDownloader)
    singleOf(::RequestedDownloads)
    single { FileEvictor(get(), get(), get(), get<FServerConfig>().evictionPreviewer) }
    singleOf(::FileMover)
    singleOf(::FileDeleter)

    singleOf(::ActionSteps)
    singleOf(::ConflictCopier)
    singleOf(::ConflictResolver)
    singleOf(::FileActionRunner)

    singleOf(::SyncLeaseRegistry)
    singleOf(::SyncModeReconciler)
    singleOf(::SyncLeaseNegotiator)

    singleOf(::PassCompletion)
    singleOf(::SourcePassExecutor)
    singleOf(::SyncRunner)
    singleOf(::AutoSyncCoordinator)
    singleOf(::SourcesController)
    singleOf(::ConflictsController)
}
