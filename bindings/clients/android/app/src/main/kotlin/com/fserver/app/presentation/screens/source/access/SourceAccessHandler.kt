package com.fserver.app.presentation.screens.source.access

import com.fserver.app.presentation.screens.source.access.model.SourceAccessIntent
import com.fserver.app.presentation.screens.source.access.model.SourceAccessState
import com.fserver.app.presentation.screens.source.shared.model.PickedSourceUi
import com.fserver.app.presentation.screens.source.shared.model.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.model.SourceFlowState
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

class SourceAccessHandler(
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
            scanPath = grant.scanPath(),
        )

        scanJob = scope.launch {
            val directory = grant.directory() ?: return@launch

            editable.update {
                it.copy(
                    phase = SourceAccessState.Phase.Scanned,
                    access = grant.accessType,
                    scanned = PickedSourceUi(
                        files = 5,
                        bytes = FileSize(100),
                        uri = directory.toString(),
                        label = directory.toString(),
                    ),
                )
            }

            // Scanner not finished yet, keep comments
            /*runCatching { directoryScanner.scan(directory).collect { onScanState(it, grant) } }
                .onFailure { error ->
                    Timber.e(error, "Failed to scan %s", directory)
                    editable.update { it.copy(phase = SourceAccessState.Phase.Denied) }
                }*/
        }
    }

    /*private fun onScanState(scan: DirectoryScanner.State, grant: SourceAccessGrant) {
        when (scan) {
            is DirectoryScanner.State.Progress -> editable.update {
                it.copy(scannedFiles = scan.scannedFiles, scannedBytes = scan.scannedSize.bytes)
            }

            is DirectoryScanner.State.Ready -> {
                val bytes = scan.files.sumOf { file -> file.size.bytes }
                val tree = grant as? SourceAccessGrant.Tree
                val access = (grant as? SourceAccessGrant.Media)?.access ?: SourceAccessUi.Full

                editable.update {
                    it.copy(
                        phase = SourceAccessState.Phase.Scanned,
                        access = access,
                        scannedFiles = scan.files.size,
                        scannedBytes = bytes,
                        scanned = PickedSourceUi(
                            files = scan.files.size,
                            bytes = bytes,
                            uri = tree?.uri?.toString(),
                            label = tree?.label.orEmpty(),
                        ),
                    )
                }
            }
        }
    }*/

    private data class Editable(
        val phase: SourceAccessState.Phase = SourceAccessState.Phase.Explaining,
        val access: SourceAccessUi = SourceAccessUi.Full,
        val scanPath: String = "",
        val scanned: PickedSourceUi? = null,
    )
}

private fun SourceAccessGrant.scanPath(): String = (this as? SourceAccessGrant.Tree)?.label ?: ""

private fun SourceAccessGrant.directory(): FoundDirectory? = when (this) {
    SourceAccessGrant.Denied -> null
    SourceAccessGrant.AllFiles -> FoundDirectory.Root("/")
    is SourceAccessGrant.Tree -> FoundDirectory.Path(uri.toString())
    is SourceAccessGrant.Media -> FoundDirectory.Media
}
