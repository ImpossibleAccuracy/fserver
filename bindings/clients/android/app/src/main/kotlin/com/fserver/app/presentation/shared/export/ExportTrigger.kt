package com.fserver.app.presentation.shared.export

import android.net.Uri
import com.fserver.app.data.export.ArchiveExporter
import com.fserver.app.presentation.shared.export.model.ExportResult
import com.fserver.app.presentation.shared.export.model.ExportUi
import com.fserver.core.external.export.ExportProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A user-started export into a picked document: one at a time, progress as state, outcome as a one-shot.
 *
 * TODO: move to a worker / foreground service, so the export survives leaving the screen.
 */
class ExportTrigger(
    private val scope: CoroutineScope,
    private val exporter: ArchiveExporter,
) {
    private val _running = MutableStateFlow<ExportUi?>(null)
    val running: StateFlow<ExportUi?> = _running.asStateFlow()

    private val _results = Channel<ExportResult>(Channel.BUFFERED)
    val results: Flow<ExportResult> = _results.receiveAsFlow()

    /** Every source, or only [sourceIds]. */
    fun run(uri: Uri, sourceIds: Set<String>? = null) {
        if (_running.value != null) return
        _running.value = ExportUi()

        scope.launch {
            val task = exporter.export(uri, sourceIds)
            task.progress.collect { _running.value = it.toUi() }
            val result = task.result().fold(
                onSuccess = { ExportResult.Finished(files = it.files, skipped = it.skipped.size) },
                onFailure = {
                    Timber.w(it, "Export failed")
                    ExportResult.Failed
                },
            )
            _running.value = null
            _results.send(result)
        }
    }
}

private fun ExportProgress.toUi() = ExportUi(
    files = files,
    totalFiles = totalFiles,
    writtenBytes = written.bytes,
    totalBytes = totalSize.bytes,
)
