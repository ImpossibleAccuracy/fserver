package com.fserver.core.crypto.internal

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.crypto.EncryptionProgress
import com.fserver.core.crypto.internal.fs.EncryptedFileSystem
import com.fserver.core.crypto.model.AtRest
import com.fserver.core.crypto.model.EncryptionPolicy
import com.fserver.core.di.BackgroundScope
import com.fserver.core.journal.JournalEvent
import com.fserver.core.journal.impl.JournalWriter
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.fs.FsFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber

/**
 * Brings every file held here in line with its source's policy: seals plaintext under `Required`,
 * opens sealed files under `Off`, reseals what another cipher or an older key sealed. One file at
 * a time, each through a part file that replaces it in one rename - a crash leaves the old file or
 * the new one, never neither (Storage Encryption §6.2). Content and version never change.
 */
internal class EncryptionMigrator(
    private val storage: FServerStorage,
    private val sourceFiles: SourceFileSystems,
    private val sealedFiles: SealedFiles,
    private val indexWriter: LocalIndexWriter,
    private val indexer: LocalChangesIndexer,
    private val backgroundScope: BackgroundScope,
    private val journal: JournalWriter,
) {
    private val running = Mutex()
    private val queued = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val runProgress = MutableStateFlow<EncryptionProgress?>(null)

    /** Files per source still waiting, as of the running migration. Absent: nothing waits. */
    val pending: StateFlow<Map<String, Int>> get() = queued

    /** The running migration over every source, null when none runs or nothing is due. */
    val progress: StateFlow<EncryptionProgress?> get() = runProgress

    /** [migrate] off the caller's coroutine; skipped while one is already running. */
    fun migrateAsync() {
        backgroundScope.launch {
            if (!running.tryLock()) return@launch
            try {
                runCatchingCancellable { migrate() }.onFailure {
                    Timber.w(
                        it,
                        "Encryption migration failed"
                    )
                }
            } finally {
                running.unlock()
            }
        }
    }

    suspend fun migrate() {
        val sources = storage.sources.all()

        // Counted from the index up front so one bar spans the run; each source corrects its own
        // count once its rescan lands.
        val estimates = sources.associate { source ->
            source.id to runCatchingCancellable { estimate(source) }.getOrNull()
        }
        val due = estimates.values.filterNotNull().filter { it.count > 0 }
        if (due.isNotEmpty()) {
            runProgress.value = EncryptionProgress(
                done = 0,
                total = due.sumOf { it.count },
                towards = due.mapTo(mutableSetOf()) { it.toward },
            )
        }

        try {
            for (source in sources) {
                runCatchingCancellable {
                    migrate(
                        source,
                        estimated = estimates[source.id]?.count ?: 0
                    )
                }.onFailure { Timber.w(it, "Could not migrate source ${source.id}") }

                queued.update { it - source.id }
            }
        } finally {
            runProgress.value = null
        }
    }

    private suspend fun estimate(source: SourceEntry): Estimate? {
        if (sourceFiles.open(source) !is EncryptedFileSystem) return null
        val target = target(source) ?: return null
        return Estimate(due(source, target).size, target.toward)
    }

    private suspend fun migrate(source: SourceEntry, estimated: Int) {
        val fs = sourceFiles.open(source) as? EncryptedFileSystem ?: return
        val target = target(source) ?: return

        if (due(source, target).isEmpty()) {
            journal.solve(JournalEvent.EncryptionIncomplete.keyOf(source.id))
            return
        }

        // A rewrite keeps the row's mtime, so an edit no scan has seen yet would hide behind it.
        val due = due(indexer.refresh(source), target)
        var migrated = 0
        var failed = 0

        runProgress.update { it?.copy(total = it.total + due.size - estimated) }

        Timber.i("Migrating ${due.size} files of source ${source.id} to $target")
        due.forEachIndexed { done, row ->
            queued.update { it + (source.id to due.size - done) }

            // One file that will not open (a key lost with Keystore, a damaged file) holds no other up.
            runCatchingCancellable { migrate(source, fs, row) }
                .onSuccess { if (it) migrated++ }
                .onFailure {
                    failed++
                    Timber.w(it, "Could not migrate ${row.path} in source ${source.id}")
                }
            runProgress.update { it?.copy(done = it.done + 1) }
        }

        val journaled =
            if (target == AtRest.Plain) JournalEvent.EncryptionTarget.Decrypted
            else JournalEvent.EncryptionTarget.Encrypted

        if (migrated > 0) journal.record(
            JournalEvent.EncryptionMigrated(
                sourceId = source.id,
                target = journaled,
                files = migrated
            )
        )

        if (failed > 0) {
            journal.raise(JournalEvent.EncryptionIncomplete(source.id, journaled, failed))
        } else {
            journal.solve(JournalEvent.EncryptionIncomplete.keyOf(source.id))
        }
    }

    /** Returns whether the file was rewritten. */
    private suspend fun migrate(
        source: SourceEntry,
        fs: EncryptedFileSystem,
        row: LocalIndexedFile
    ): Boolean {
        // Gone since the last scan: the next one records that.
        val original = fs.openFile(row.locator) ?: return false
        val size = original.rawSize()
        val rewrite = fs.rewrite(original, row.path)

        // A file written meanwhile changes its row or its length; the rewrite then describes old bytes.
        val replaced = indexWriter.recordRewritten(source, row) {
            val current = fs.openFile(row.locator)
            if (current?.rawSize() == size) rewrite.commit() else null
        }
        if (!replaced) {
            Timber.i("Skipped ${row.path} in source ${source.id}: changed while it was rewritten")
            rewrite.discard()
        }
        return replaced
    }

    private suspend fun due(source: SourceEntry, target: AtRest) =
        due(storage.index.processedFiles(source.id), target)

    private fun due(rows: List<LocalIndexedFile>, target: AtRest) =
        rows.filter { it.state is LocalIndexedFile.State.Present && it.atRest != target }

    /** How the policy wants files to sit, or null when it cannot be met here. */
    private suspend fun target(source: SourceEntry): AtRest? =
        when (val policy = source.preferences.encryption) {
            EncryptionPolicy.Off -> AtRest.Plain

            is EncryptionPolicy.Required ->
                if (!sealedFiles.supports(policy.cipherId)) {
                    Timber.w("Source ${source.id} asks for unregistered cipher ${policy.cipherId}")
                    null
                } else {
                    AtRest.Sealed(
                        cipherId = policy.cipherId,
                        keyId = storage.storageKeys.current(source.id).id
                    )
                }
        }
}

private suspend fun FsFile.rawSize(): Long =
    ((this as? SealedFsFile)?.raw ?: this).openReader().use { it.size() }

private class Estimate(val count: Int, val toward: EncryptionProgress.Toward)

private val AtRest.toward: EncryptionProgress.Toward
    get() = if (this == AtRest.Plain) EncryptionProgress.Toward.Decrypted else EncryptionProgress.Toward.Encrypted
