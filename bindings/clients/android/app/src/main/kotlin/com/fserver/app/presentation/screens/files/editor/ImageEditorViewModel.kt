package com.fserver.app.presentation.screens.files.editor

import com.fserver.app.util.stateInScreen
import com.fserver.app.presentation.composable.model.fileName
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attafitamim.krop.core.crop.CropState
import com.attafitamim.krop.core.crop.createResult
import com.attafitamim.krop.core.crop.cropState
import com.attafitamim.krop.core.crop.flipHorizontal
import com.attafitamim.krop.core.crop.rotLeft
import com.attafitamim.krop.core.crop.rotRight
import com.attafitamim.krop.core.images.ImageStreamSrc
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.files.editor.model.ImageEditorIntent
import com.fserver.app.presentation.screens.files.editor.model.ImageEditorState
import com.fserver.app.presentation.screens.files.editor.model.ImageEditorUiEffect
import com.fserver.app.presentation.screens.files.editor.shared.EditableImageFormat
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.FilesController
import com.fserver.core.files.access.SourceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

class ImageEditorViewModel(
    private val key: Destination.Files.ImageEditor,
    private val filesController: FilesController,
    private val reporter: ErrorReporter,
) : ViewModel() {
    private val effects = Channel<ImageEditorUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<ImageEditorState> = editable
        .map { it.toPresentation() }
        .stateInScreen(viewModelScope, ImageEditorState())

    init {
        load()
    }

    fun onIntent(intent: ImageEditorIntent) {
        when (intent) {
            ImageEditorIntent.RotateLeft -> editable.value.crop?.rotLeft()
            ImageEditorIntent.RotateRight -> editable.value.crop?.rotRight()
            ImageEditorIntent.FlipHorizontal -> editable.value.crop?.flipHorizontal()
            ImageEditorIntent.Reset -> editable.value.crop?.reset()
            ImageEditorIntent.SaveRequested -> save()
            ImageEditorIntent.RetryRequested -> load()
        }
    }

    private fun load() {
        editable.update { it.copy(status = ImageEditorState.StatusUi.Loading) }

        viewModelScope.launch {
            runCatchingCancellable {
                val file = requireFile()
                val format = EditableImageFormat.of(file.path)
                    ?: throw IllegalArgumentException("Not an editable image: ${file.path}")
                val bytes = withContext(Dispatchers.IO) { file.read().use { it.readBytes() } }
                val src = ImageStreamSrc({ bytes.inputStream() })
                    ?: throw IllegalArgumentException("Could not decode ${file.path}")

                Editable(
                    fileName = file.path.fileName(),
                    status = ImageEditorState.StatusUi.Ready,
                    format = format,
                    crop = cropState(src),
                )
            }
                .onSuccess { loaded -> editable.value = loaded }
                .onFailure { error ->
                    reporter.report(error, "Loading ${key.fileId} into the image editor failed")
                    editable.update { it.copy(status = ImageEditorState.StatusUi.Failed) }
                }
        }
    }

    private fun save() {
        val current = editable.value
        val crop = current.crop ?: return
        val format = current.format ?: return
        if (current.status != ImageEditorState.StatusUi.Ready) return

        editable.update { it.copy(status = ImageEditorState.StatusUi.Saving) }

        viewModelScope.launch {
            runCatchingCancellable {
                val bitmap = crop.createResult(maxSize = null)
                    ?: throw IllegalStateException("Could not render the edited ${key.fileId}")
                val bytes = withContext(Dispatchers.Default) {
                    format.encode(bitmap.asAndroidBitmap())
                }

                requireFile().write { writer ->
                    writer.write(offset = 0, bytes = bytes)
                    writer.truncate(bytes.size.toLong())
                }
            }
                .onSuccess { effects.send(ImageEditorUiEffect.NavigateBack) }
                .onFailure { error ->
                    reporter.report(error, "Saving the edited ${key.fileId} failed")
                    editable.update { it.copy(status = ImageEditorState.StatusUi.Ready) }
                }
        }
    }

    private suspend fun requireFile(): SourceFile =
        filesController.file(key.sourceId, key.fileId)
            ?: throw FileNotFoundException("File ${key.fileId} of ${key.sourceId} is not held here")

    private data class Editable(
        val fileName: String = "",
        val status: ImageEditorState.StatusUi = ImageEditorState.StatusUi.Loading,
        val format: EditableImageFormat? = null,
        val crop: CropState? = null,
    ) {
        fun toPresentation() = ImageEditorState(
            fileName = fileName,
            status = status,
            crop = crop,
        )
    }
}
