package com.fserver.core.external.export

import com.fserver.common.model.FileSize
import com.fserver.common.task.ProgressTask
import com.fserver.common.task.progressTask
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.external.impl.ManifestDto
import com.fserver.core.external.impl.toDto
import com.fserver.core.files.access.LocalFileEditor
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Everything this device holds - files, their metadata, settings - as one ZIP (ТЗ §3.9).
 *
 * Files go in as plaintext, also from encrypted sources. Evicted and peer-only files get metadata
 * only: nothing is downloaded. Secrets (identity key, storage keys, auth password/PIN) stay out.
 */
class DataExport internal constructor(
    private val storage: FServerStorage,
    private val editor: LocalFileEditor,
    private val requirementsChecker: RequirementsChecker,
    private val timeProvider: TimeProvider,
) {
    /**
     * Writes the archive into [out], leaving it open: the caller closes it, and deletes what was
     * written if the task fails or is canceled.
     *
     * @param sourceIds the sources to export, or null for all of them. A partial export keeps only
     *  their peers among the trusted devices.
     * @param hostEntries host-owned files (its own settings, say), stored as `host/<name>`.
     */
    fun export(
        out: OutputStream,
        sourceIds: Set<String>? = null,
        hostEntries: Map<String, ByteArray> = emptyMap(),
    ): ProgressTask<ExportProgress, ExportReport> {
        require(hostEntries.keys.all { it.isNotBlank() && it.none { c -> c == '/' || c == '\\' } }) {
            "Host entry names must be plain file names: ${hostEntries.keys}"
        }

        return progressTask {
            write(out, sourceIds, hostEntries)
        }
    }

    private suspend fun ProducerScope<ExportProgress>.write(
        out: OutputStream,
        sourceIds: Set<String>?,
        hostEntries: Map<String, ByteArray>,
    ): ExportReport = withContext(Dispatchers.IO) {
        val device = storage.identity.localDevice()
        val selected = storage.sources.all().filter { sourceIds == null || it.id in sourceIds }
        sourceIds?.let { ids ->
            val missing = ids - selected.mapTo(mutableSetOf()) { it.id }
            require(missing.isEmpty()) { "Sources $missing are not registered" }
        }
        val peers = selected.mapTo(mutableSetOf()) { it.deviceId }
        val trusted = storage.trust.devices.first().filter { sourceIds == null || it.deviceId in peers }

        val sources = selected.map { source ->
            SourceContent(
                source = source,
                local = storage.index.processedFiles(source.id).filterNot { it.isDeleted },
                remote = storage.remoteIndex.files(source.id).filterNot { it.isDeleted },
            )
        }

        val progress = Progress(
            totalFiles = sources.sumOf { it.present.size },
            totalBytes = sources.sumOf { s -> s.present.sumOf { it.size.bytes } },
        )
        val skipped = mutableListOf<ExportReport.SkippedFile>()
        var archivedBytes = 0L
        val zip = ZipOutputStream(out)

        zip.json("settings/device.json", device.toDto())
        zip.json("settings/auth.json", storage.auth.offeredMethods.value.toDto())
        zip.json("settings/trusted-devices.json", trusted.map { it.toDto() })
        zip.json("settings/sources.json", sources.map { it.source.toDto() })
        hostEntries.forEach { (name, bytes) -> zip.entry("host/$name") { it.write(bytes) } }

        send(progress.snapshot())

        for (content in sources) {
            val archived = mutableSetOf<String>()
            val unreachable = runCatchingCancellable {
                requirementsChecker.forSource(content.source.location)
            }.getOrNull()?.isSatisfied != true

            for (file in content.present) {
                val written = when {
                    unreachable -> Result.failure(
                        IllegalStateException("Source ${content.source.label} is not reachable")
                    )

                    // Only reading the source is forgiven: a failing `out` fails the whole export.
                    else -> try {
                        Result.success(file(zip, content.source, file, progress))
                    } catch (e: ReadFailure) {
                        Result.failure(e.cause ?: e)
                    }
                }

                written
                    .onSuccess {
                        archived += file.fileId
                        archivedBytes += it
                    }
                    .onFailure {
                        skipped += ExportReport.SkippedFile(
                            file.sourceId,
                            file.path,
                            it.message ?: it.toString()
                        )
                    }
                progress.files++
                send(progress.snapshot())
            }

            zip.json("metadata/${content.source.id}.json", content.metadata(archived))
        }

        val report = ExportReport(
            files = progress.files - skipped.size,
            size = FileSize(archivedBytes),
            metadataOnly = sources.sumOf { it.local.size + it.peerOnly.size - it.present.size },
            skipped = skipped,
        )

        zip.json(
            "manifest.json",
            ManifestDto(
                exportedAt = timeProvider.now().toString(),
                scope = if (sourceIds == null) ManifestDto.Scope.All else ManifestDto.Scope.Sources,
                device = device.toDto(),
                sources = sources.map { ManifestDto.SourceRefDto(it.source.id, it.source.label) },
                files = report.files,
                bytes = report.size.bytes,
                metadataOnly = report.metadataOnly,
                skipped = skipped.size,
            ),
        )
        zip.finish()
        zip.flush()

        report
    }

    /** Streams one file's plaintext in, returning its size. A failure midway leaves a truncated entry behind. */
    private suspend fun ProducerScope<ExportProgress>.file(
        zip: ZipOutputStream,
        source: SourceEntry,
        file: LocalIndexedFile,
        progress: Progress,
    ): Long = withContext(Dispatchers.IO) {
        var written = 0L
        val input =
            reading { editor.read(IndexedFileKey(fileId = file.fileId, sourceId = file.sourceId)) }
        zip.setLevel(Deflater.BEST_SPEED)
        try {
            zip.entry("files/${source.id}/${file.path.trimStart('/')}") {
                val buffer = ByteArray(BufferSize)
                while (true) {
                    val read = reading { input.read(buffer) }
                    if (read < 0) break
                    it.write(buffer, 0, read)
                    written += read
                    progress.bytes += read
                    if (written % ProgressStep < read) send(progress.snapshot())
                }
            }
        } finally {
            input.close()
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
        }

        written
    }

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> ZipOutputStream.json(name: String, value: T) =
        entry(name) { Archive.encodeToStream(value, NonClosing(it)) }

    private inline fun ZipOutputStream.entry(name: String, block: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        try {
            block(this)
        } finally {
            closeEntry()
        }
    }

    private inline fun <T> reading(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ReadFailure(e)
    }

    private class ReadFailure(cause: Throwable) : Exception(cause)

    private class SourceContent(
        val source: SourceEntry,
        val local: List<LocalIndexedFile>,
        remote: List<RemoteIndexedFile>,
    ) {
        val present = local.filter { it.state is LocalIndexedFile.State.Present }
        val peerOnly: List<RemoteIndexedFile> = local.mapTo(mutableSetOf()) { it.fileId }
            .let { known -> remote.filterNot { it.fileId in known } }

        fun metadata(archived: Set<String>) =
            local.map { it.toDto(archived = it.fileId in archived) } + peerOnly.map { it.toDto() }
    }

    private class Progress(val totalFiles: Int, val totalBytes: Long) {
        var files = 0
        var bytes = 0L

        fun snapshot() = ExportProgress(
            files = files,
            totalFiles = totalFiles,
            written = FileSize(bytes),
            totalSize = FileSize(totalBytes),
        )
    }

    /** `encodeToStream` closes nothing today, but the zip must outlive every entry regardless. */
    private class NonClosing(private val delegate: OutputStream) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() = delegate.flush()
        override fun close() = Unit
    }

    private companion object {
        const val BufferSize = 64 * 1024
        const val ProgressStep = 1024 * 1024
        val Archive = Json {
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = true
        }
    }
}
