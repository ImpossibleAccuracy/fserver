package com.fserver.core.support

import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.LocalFileHasher
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.index.LocalVersions
import com.fserver.core.sync.index.SourceIndexLocks
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode

/** The local index classes, wired the way Koin wires them: one lock set shared by all. */
internal class LocalIndex(
    storage: FServerStorage,
    node: FilesNode,
    clock: TimeProvider,
    progress: SyncProgressReporter = SyncProgressReporter(clock),
) {
    private val locks = SourceIndexLocks()
    private val versions = LocalVersions(storage, HybridLogicalClock(storage, clock))

    val writer = LocalIndexWriter(storage, clock, locks, versions)
    val hasher = LocalFileHasher(node, writer)
    val indexer = LocalChangesIndexer(storage, node, FakeRequirementsChecker(), clock, locks, versions, hasher, progress)
}
