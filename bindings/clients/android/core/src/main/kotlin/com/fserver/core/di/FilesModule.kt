package com.fserver.core.di

import com.fserver.core.disk.DiskUsageRepository
import com.fserver.core.files.FilesController
import com.fserver.core.files.gc.GarbageCollector
import com.fserver.files.FilesNode
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Reading the filesystem: the `:files` node, the walk before registering a source, disk usage. */
internal val filesModule = module {
    single { FilesNode.create(get()) }

    singleOf(::FilesController)
    singleOf(::GarbageCollector)
    singleOf(::DiskUsageRepository)
}
