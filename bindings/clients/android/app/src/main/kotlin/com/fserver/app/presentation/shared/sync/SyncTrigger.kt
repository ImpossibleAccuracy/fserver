package com.fserver.app.presentation.shared.sync

import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A user-started pass: one at a time, failures reported rather than thrown. */
class SyncTrigger(
    private val scope: CoroutineScope,
    private val sourcesController: SourcesController,
    private val reporter: ErrorReporter,
) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Every source, or only [sourceId] - then forced past the device constraints. */
    fun run(context: String, sourceId: String? = null) {
        if (_running.value) return
        _running.value = true

        scope.launch {
            runCatching {
                if (sourceId == null) sourcesController.runSync()
                else sourcesController.runSync(sourceId, force = true)
            }.exceptionOrNull()?.let { reporter.report(it, context) }

            _running.value = false
        }
    }
}
