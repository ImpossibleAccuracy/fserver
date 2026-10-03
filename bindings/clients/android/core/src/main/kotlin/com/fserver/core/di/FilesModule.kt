package com.fserver.core.di

import com.fserver.core.FServerConfig
import com.fserver.core.crypto.EncryptionController
import com.fserver.core.crypto.internal.EncryptionMigrator
import com.fserver.core.crypto.internal.SealedFiles
import com.fserver.core.crypto.internal.SourceFileSystems
import com.fserver.core.disk.DiskUsageRepository
import com.fserver.core.external.export.DataExport
import com.fserver.core.files.FilesController
import com.fserver.core.files.access.LocalFileEditor
import com.fserver.core.files.gc.GarbageCollector
import com.fserver.core.store.FServerStorage
import com.fserver.files.FilesNode
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Reading the filesystem: the `:files` node, the walk before registering a source, disk usage, export. */
internal val filesModule = module {
    single { FilesNode.create(get()) }
    single { SealedFiles(get<FServerConfig>().storageCiphers, get<FServerStorage>().storageKeys) }
    singleOf(::SourceFileSystems)
    singleOf(::EncryptionMigrator)
    singleOf(::EncryptionController)

    singleOf(::FilesController)
    singleOf(::LocalFileEditor)
    singleOf(::GarbageCollector)
    singleOf(::DataExport)
    single { DiskUsageRepository(get(), get(), get<FServerConfig>().evictionPreviewer) }
}
