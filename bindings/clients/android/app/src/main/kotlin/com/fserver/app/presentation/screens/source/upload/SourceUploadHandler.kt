package com.fserver.app.presentation.screens.source.upload

import com.fserver.app.presentation.screens.source.shared.model.SourceFlowState
import com.fserver.app.presentation.screens.source.upload.model.SourceUploadState
import com.fserver.app.presentation.screens.source.upload.model.SourceUploadUiEffect
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.SourceEntry
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class SourceUploadHandler(
    private val sourcesRepository: RegisteredSourcesRepository,
    private val sourcesController: SourcesController,
    private val devicesRepository: DevicesRepository,

    private val flow: MutableStateFlow<SourceFlowState>,
    private val scope: CoroutineScope,
) {
    private val editable = MutableStateFlow(Editable())

    private val effectChannel = Channel<SourceUploadUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var syncJob: Job? = null

    private val entry = flow.map { it.sourceId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else sourcesRepository.observeById(id)
        }

    private val targetDevice = flow.map { it.targetDeviceId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else devicesRepository.device(id)
        }

    val state: StateFlow<SourceUploadState?> =
        combine(flow, editable, entry, targetDevice) { shared, local, source, device ->
            if (shared.sourceId == null) return@combine null

            val refusal = (source?.status as? SourceEntry.Status.Disabled)?.reason

            SourceUploadState(
                phase = when {
                    refusal != null -> SourceUploadState.Phase.Refused
                    local.syncing -> SourceUploadState.Phase.Syncing
                    else -> SourceUploadState.Phase.WaitingForPeer
                },
                targetName = device?.displayName.orEmpty(),
                sourceLabel = source?.label ?: shared.source?.label.orEmpty(),
                files = shared.source?.files ?: 0,
                progress = local.progress,
                progressDetail = local.progressDetail,
                reason = refusal,
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        scope.launch {
            entry.collect { source ->
                if (source?.status == SourceEntry.Status.Active) startSync()
            }
        }
    }

    fun reset() {
        syncJob?.cancel()
        syncJob = null
        editable.value = Editable()
    }

    /**
     * The first pass, once the peer has taken the source on.
     *
     * The bar is a stand-in: the engine reports nothing per-file yet, so the pass is kicked off
     * and the progress is counted off the file total the scan already produced.
     */
    private fun startSync() {
        if (syncJob != null) return

        syncJob = scope.launch {
            /*launch {
                runCatching { sourcesController.runSync() }
                    .exceptionOrNull()
                    ?.let { Timber.w(it, "First pass failed") }
            }*/

            val files = flow.value.source?.files ?: 0
            editable.update { it.copy(syncing = true, progress = 0f) }

            repeat(ProgressSteps) { step ->
                delay(StepDelayMillis)

                val done = step + 1
                editable.update {
                    it.copy(
                        progress = done.toFloat() / ProgressSteps,
                        progressDetail = "${files * done / ProgressSteps} of $files",
                    )
                }
            }

            effectChannel.send(SourceUploadUiEffect.NavigateToDone)
        }
    }

    private data class Editable(
        val syncing: Boolean = false,
        val progress: Float = 0f,
        val progressDetail: String = "",
    )

    private companion object {
        const val ProgressSteps = 20
        const val StepDelayMillis = 150L
    }
}
