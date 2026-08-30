package com.fserver.app.presentation.screens.source.access

import android.content.Context
import com.fserver.app.presentation.screens.source.access.model.SourceAccessIntent
import com.fserver.app.presentation.screens.source.access.model.SourceAccessState
import com.fserver.app.presentation.screens.source.shared.model.PickedSourceUi
import com.fserver.app.presentation.screens.source.shared.model.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.model.SourceFlowState
import com.fserver.core.files.FilesController
import com.fserver.core.files.model.DirectoryScanProgress
import com.fserver.core.files.model.FileSize
import com.fserver.core.files.model.FoundDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class SourceAccessHandler(
    private val context: Context,
    private val filesController: FilesController,

    private val flow: MutableStateFlow<SourceFlowState>,
    private val scope: CoroutineScope,
) {
    private val editable = MutableStateFlow(Editable())

    private var scanJob: Job? = null

    val state: StateFlow<SourceAccessState?> = combine(flow, editable) { shared, local ->
        SourceAccessState(
            kind = shared.kind ?: return@combine null,
            phase = local.phase,
            access = local.access,
            scanned = local.scanned,
            progress = local.progress,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun onIntent(intent: SourceAccessIntent) {
        when (intent) {
            is SourceAccessIntent.AccessAnswered -> when (intent.grant) {
                SourceAccessGrant.Denied ->
                    editable.update { it.copy(phase = SourceAccessState.Phase.Denied) }

                else -> startScan(intent.grant)
            }

            SourceAccessIntent.ScanCancelled -> {
                scanJob?.cancel()
                editable.update { it.copy(phase = SourceAccessState.Phase.Explaining) }
            }

            SourceAccessIntent.Confirmed -> commit()
        }
    }

    fun reset() {
        scanJob?.cancel()
        editable.value = Editable()
    }

    private fun commit() {
        val local = editable.value
        flow.update { it.copy(access = local.access, source = local.scanned) }
    }

    private fun startScan(grant: SourceAccessGrant) {
        scanJob?.cancel()
        editable.value = Editable(
            phase = SourceAccessState.Phase.Scanning,
            access = grant.accessType,
        )

        scanJob = scope.launch {
            val directory = grant.directory() ?: return@launch

            editable.update {
                it.copy(
                    scanned = null,
                    progress = DirectoryScanProgress(
                        scannedFiles = 0,
                        scannedSize = FileSize(0),
                    )
                )
            }

            // Scanner not finished yet, keep comments
            filesController
                .loadContent(
                    directory = directory,
                    onProgress = { progress ->
                        editable.update {
                            it.copy(progress = progress)
                        }
                    }
                )
                .fold(
                    onSuccess = { files ->
                        val bytes = files.sumOf { it.size.bytes }
                        val tree = grant as? SourceAccessGrant.Tree
                        val access =
                            (grant as? SourceAccessGrant.Media)?.access ?: SourceAccessUi.Full

                        editable.update {
                            it.copy(
                                phase = SourceAccessState.Phase.Scanned,
                                access = access,
                                scanned = PickedSourceUi(
                                    files = files.size,
                                    bytes = FileSize(bytes),
                                    uri = tree?.uri?.toString(),
                                    label = tree?.label.orEmpty(),
                                ),
                            )
                        }
                    },
                    onFailure = {
                        Timber.e(it)

                        // TODO
                        editable.update { it.copy(phase = SourceAccessState.Phase.Denied) }
                    }
                )
        }
    }

    private fun SourceAccessGrant.directory(): FoundDirectory? = when (this) {
        SourceAccessGrant.Denied -> null
        SourceAccessGrant.AllFiles -> FoundDirectory.Root.fromContext(context)
        is SourceAccessGrant.Tree -> FoundDirectory.Tree(uri.toString())
        is SourceAccessGrant.Media -> FoundDirectory.Media
    }

    private data class Editable(
        val phase: SourceAccessState.Phase = SourceAccessState.Phase.Explaining,
        val access: SourceAccessUi = SourceAccessUi.Full,
        val scanned: PickedSourceUi? = null,
        val progress: DirectoryScanProgress? = null,
    )
}
