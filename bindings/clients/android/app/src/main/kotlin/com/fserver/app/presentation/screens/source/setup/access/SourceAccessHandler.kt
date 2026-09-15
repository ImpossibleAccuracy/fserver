package com.fserver.app.presentation.screens.source.setup.access

import android.content.Context
import com.fserver.app.presentation.error.ErrorReporter
import com.fserver.app.presentation.screens.source.setup.access.model.SourceAccessIntent
import com.fserver.app.presentation.screens.source.setup.access.model.SourceAccessState
import com.fserver.app.presentation.screens.source.setup.access.model.SourceAccessUiEffect
import com.fserver.app.presentation.screens.source.setup.shared.model.PickedSourceUi
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceAccessUi
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceSetupState
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreviewSelection
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.screens.source.shared.preview.model.toPreview
import com.fserver.common.model.FileSize
import com.fserver.core.files.FilesController
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.StorageVolumes
import com.fserver.core.files.scan.DirectoryScanProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SourceAccessHandler(
    private val context: Context,
    private val filesController: FilesController,

    private val flow: MutableStateFlow<SourceSetupState>,
    private val scope: CoroutineScope,
    private val reporter: ErrorReporter,
) {
    private val editable = MutableStateFlow(Editable())

    private val effectChannel = Channel<SourceAccessUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var scanJob: Job? = null

    val state: StateFlow<SourceAccessState?> = combine(flow, editable) { shared, local ->
        val previewSelection = if (local.selectable) SourcePreviewSelection(
            selected = local.selection,
            onSelectFile = null,
            onSelectDirectory = { entry ->
                editable.update { it.copy(selection = entry) }
            },
            walkUp = {
                editable.update { it.copy(selection = it.parentOfSelection()) }
            }
        ) else null

        SourceAccessState(
            kind = shared.kind ?: return@combine null,
            phase = local.phase,
            access = local.access,
            label = local.label,
            files = local.files,
            bytes = local.bytes,
            preview = local.preview,
            progress = local.progress,
            selection = previewSelection
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
                editable.update {
                    it.copy(
                        phase = SourceAccessState.Phase.Explaining,
                        selectable = false,
                    )
                }
            }

            SourceAccessIntent.DirectoryConfirmed -> confirmDirectory()

            SourceAccessIntent.Confirmed -> commit()
        }
    }

    fun reset() {
        scanJob?.cancel()
        editable.value = Editable()
    }

    private fun commit() {
        val local = editable.value
        val location = local.location ?: return

        flow.update {
            it.copy(
                access = local.access,
                source = PickedSourceUi(
                    label = local.label,
                    files = local.files,
                    bytes = local.bytes,
                    location = location,
                ),
            )
        }
    }

    /**
     * Narrows a whole-device scan to the folder the user picked and walks that alone.
     *
     * The second walk is what produces the counts the rest of the flow reports, so it replaces
     * the first rather than filtering it: nothing outside the folder is a source anymore.
     */
    private fun confirmDirectory() {
        val entry = editable.value.selection ?: return

        if (entry !is SourcePreviewUi.Directory) {
            throw IllegalStateException("Expected a directory, got $entry")
        }

        runScan(
            target = SourceLocation.Directory(entry.path),
            selectable = SourceLocation.Directory(entry.path),
            access = editable.value.access,
            label = entry.name,
            previewFiles = false,
        )
    }

    private fun startScan(grant: SourceAccessGrant) {
        val target = grant.directory() ?: return
        val tree = grant as? SourceAccessGrant.Tree

        runScan(
            target = target,
            selectable = target as? SourceLocation.Selectable,
            access = grant.accessType,
            label = tree?.label.orEmpty(),
            previewFiles = true,
        )
    }

    /** What a whole-device walk found its files on — the top level of the folder tree. */
    private fun volumesOf(target: SourceLocation): List<SourceLocation.Root.Volume> =
        (target as? SourceLocation.Root)?.volumes.orEmpty()

    /**
     * Walks [target] and puts what it found on screen.
     *
     * [selectable] is null only while the whole device is being walked: everything is not a source
     * anyone may register, so that scan exists to produce the folder list and nothing else. With
     * [previewFiles] off the scan is the second one over a confirmed folder, and the flow moves on
     * by itself — the user has already seen these files once.
     */
    private fun runScan(
        target: SourceLocation,
        selectable: SourceLocation.Selectable?,
        access: SourceAccessUi,
        label: String,
        previewFiles: Boolean,
    ) {
        scanJob?.cancel()
        editable.value = Editable(
            phase = SourceAccessState.Phase.Scanning,
            access = access,
            label = label,
            location = selectable,
            progress = DirectoryScanProgress(scannedFiles = 0, scannedSize = FileSize(0)),
            selectable = false,
        )

        scanJob = scope.launch {
            val kind = flow.value.kind ?: return@launch
            val task = filesController.loadContent(directory = target)

            task.progress.collect { progress ->
                editable.update { it.copy(progress = progress) }
            }

            task.result().fold(
                onSuccess = { files ->
                    editable.update {
                        it.copy(
                            phase = SourceAccessState.Phase.Scanned,
                            files = files.size,
                            bytes = FileSize(files.sumOf { file -> file.size.bytes }),
                            preview = if (previewFiles) {
                                files.toPreview(kind, volumesOf(target))
                            } else {
                                null
                            },
                        )
                    }

                    if (previewFiles) {
                        editable.update {
                            it.copy(
                                selectable = target is SourceLocation.Root
                            )
                        }
                    } else {
                        commit()
                        effectChannel.send(SourceAccessUiEffect.NavigateToMode)
                    }
                },
                onFailure = {
                    reporter.report(it, "Could not read the picked source")

                    // TODO: a scan that broke is not the same as access refused; the phase needs
                    //  a third case before the screen can say which happened.
                    editable.update { it.copy(phase = SourceAccessState.Phase.Denied) }
                }
            )
        }
    }

    /** Where a back gesture inside the preview lands: one folder up, or the top of the tree. */
    private fun Editable.parentOfSelection(): SourcePreviewUi.Directory? =
        (preview as? SourcePreviewUi.Tree)?.parentOf(selection)

    private fun SourceAccessGrant.directory(): SourceLocation? = when (this) {
        SourceAccessGrant.Denied -> null
        SourceAccessGrant.AllFiles -> StorageVolumes.fromContext(context)
        is SourceAccessGrant.Tree -> SourceLocation.Tree(uri.toString())
        is SourceAccessGrant.Media -> SourceLocation.Media
        SourceAccessGrant.Internal -> SourceLocation.Internal(bucket = DevSourceBucket)
    }

    private data class Editable(
        val phase: SourceAccessState.Phase = SourceAccessState.Phase.Explaining,
        val access: SourceAccessUi = SourceAccessUi.Full,
        val label: String = "",
        val location: SourceLocation.Selectable? = null,
        val files: Int = 0,
        val bytes: FileSize = FileSize(0),
        val preview: SourcePreviewUi? = null,
        val progress: DirectoryScanProgress? = null,
        val selectable: Boolean = false,
        val selection: SourcePreviewUi.PreviewContentEntry? = null
    )
}
