package com.fserver.app.presentation.screens.files.picker

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.FileKindUi
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerIntent
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState.PickerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.uuid.Uuid

/** Newest media first, capped — the selection list renders every row eagerly. */
private const val MEDIA_SCAN_LIMIT = 500

class FilesPickerViewModel(
    @SuppressLint("StaticFieldLeak") private val context: Context,
) : ViewModel() {
    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<FilesPickerState> = editable
        .map {
            FilesPickerState(
                sources = it.sources,
                entries = it.documents.map { dir ->
                    dir.toPresentation()
                }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FilesPickerState(),
        )

    init {
        editable.update {
            it.copy(
                sources = buildList {
                    add(PickerSource.StorageAccessFramework)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        add(PickerSource.FullAccess)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        add(PickerSource.MediaStore)
                    }
                }
            )
        }
    }

    fun onIntent(intent: FilesPickerIntent) {
        when (intent) {
            is FilesPickerIntent.EntryRemoved ->
                editable.update {
                    it.copy(
                        documents = it.documents.filterNot { dir ->
                            dir.id == intent.entry.id
                        }
                    )
                }

            is FilesPickerIntent.UriPicked -> {
                viewModelScope.launch {
                    handleUri(intent.uri)
                }
            }

            FilesPickerIntent.FullAccessGranted -> {
                viewModelScope.launch {
                    scanRoots()
                }
            }

            FilesPickerIntent.MediaAccessGranted -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    viewModelScope.launch {
                        scanMedia()
                    }
                }
            }
        }
    }

    private suspend fun handleUri(uri: Uri) {
        try {
            val document = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(context, uri)
                    ?: DocumentFile.fromSingleUri(context, uri)
                    ?: throw IllegalArgumentException("Invalid URI: $uri")
            }

            val entries = buildList {
                add(createDocumentEntry(document))

                if (document.isDirectory) {
                    document.listFiles().forEach { child ->
                        add(createDocumentEntry(child))
                    }
                }
            }

            editable.update { state ->
                state.copy(
                    documents = state.documents.mergedWith(entries),
                )
            }
        } catch (t: Throwable) {
            Log.e("FilesPickerViewModel", "Error handling URI: $uri", t)
        }
    }

    private suspend fun scanRoots() {
        val dirs = withContext(Dispatchers.IO) {
            val roots = storageRoots()
            val rootContents = roots.flatMap {
                it.listFiles()?.toList() ?: emptyList()
            }

            rootContents.mapNotNull {
                createDocumentEntry(
                    DocumentFile.fromFile(it)
                )
            }
        }

        editable.update {
            it.copy(
                documents = it.documents.mergedWith(dirs)
            )
        }
    }

    /**
     * Reads the media collections the user just granted access to.
     *
     * Only what the grant covers comes back: a partial (user-selected) grant on API 34+ yields
     * exactly the chosen items, and a media type left denied simply contributes no rows — the
     * query itself does not fail.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun scanMedia() = withContext(Dispatchers.IO) {
        val media = try {
            queryMedia()
        } catch (t: Throwable) {
            Log.e("FilesPickerViewModel", "Error querying MediaStore", t)
            return@withContext
        }

        editable.update {
            it.copy(
                documents = it.documents.mergedWith(media)
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryMedia(): List<Editable.Entry.Media> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString(),
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        return buildList {
            context.contentResolver
                .query(collection, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameIndex =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val mimeIndex =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                    while (cursor.moveToNext() && size < MEDIA_SCAN_LIMIT) {
                        add(
                            Editable.Entry.Media(
                                id = Uuid.random().toString(),
                                uri = ContentUris.withAppendedId(
                                    collection,
                                    cursor.getLong(idIndex),
                                ),
                                name = cursor.getString(nameIndex) ?: "Unknown",
                                mimeType = cursor.getString(mimeIndex),
                                sizeLabel = Formatter.formatShortFileSize(
                                    context,
                                    cursor.getLong(sizeIndex),
                                ),
                            )
                        )
                    }
                }
        }
    }

    private fun storageRoots(): List<File> {
        val sm = context.getSystemService(StorageManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sm.storageVolumes.mapNotNull { it.directory }
        } else {
            listOfNotNull(Environment.getExternalStorageDirectory())
        }
    }

    private fun createDocumentEntry(document: DocumentFile) = when {
        document.isDirectory -> {
            Editable.Entry.Directory(
                id = Uuid.random().toString(),
                document = document,
                content = document.listFiles().toList(),
            )
        }

        else -> {
            Editable.Entry.File(
                id = Uuid.random().toString(),
                document = document,
            )
        }
    }
}

/** Appends only what is not selected yet; identity is the path, since ids are minted per pick. */
private fun List<Editable.Entry>.mergedWith(new: List<Editable.Entry>): List<Editable.Entry> {
    val known = mapTo(mutableSetOf()) { it.path }
    return this + new.filterNot { it.path in known }
}

private fun fileKindOf(mimeType: String?): FileKindUi = when {
    mimeType == null -> FileKindUi.Other
    mimeType.startsWith("image/") -> FileKindUi.Image
    mimeType.startsWith("video/") -> FileKindUi.Video
    mimeType.startsWith("audio/") -> FileKindUi.Audio
    else -> FileKindUi.Document
}

private data class Editable(
    val sources: List<PickerSource> = emptyList(),
    val isLoading: Boolean = false,
    val documents: List<Entry> = emptyList(),
) {
    /**
     * A selected thing, whichever picker produced it. The id is minted once, when the entry is
     * picked, and carried into the UI model — removal matches on it, so it must not be re-rolled
     * on every state emission.
     */
    sealed interface Entry {
        val id: String
        val path: String

        fun toPresentation(): FilesPickerState.PickedEntryUi

        data class Directory(
            override val id: String,
            val document: DocumentFile,
            val content: List<DocumentFile>,
        ) : Entry {
            override val path: String get() = document.uri.toString()

            override fun toPresentation() = FilesPickerState.PickedEntryUi(
                id = id,
                name = document.name ?: "Unknown",
                path = path,
                kind = FileKindUi.Folder,
                isDirectory = true,
                detailLabel = when {
                    content.isEmpty() -> "Empty"
                    else -> "${content.size} files"
                },
            )
        }

        data class File(
            override val id: String,
            val document: DocumentFile,
        ) : Entry {
            override val path: String get() = document.uri.toString()

            override fun toPresentation() = FilesPickerState.PickedEntryUi(
                id = id,
                name = document.name ?: "Unknown",
                path = path,
                kind = fileKindOf(document.type),
                isDirectory = false,
                detailLabel = null,
            )
        }

        /**
         * A row from MediaStore. Its columns are read once, during the scan, rather than kept as
         * a [DocumentFile] — every metadata read on a media URI costs another resolver query.
         */
        data class Media(
            override val id: String,
            val uri: Uri,
            val name: String,
            val mimeType: String?,
            val sizeLabel: String?,
        ) : Entry {
            override val path: String get() = uri.toString()

            override fun toPresentation() = FilesPickerState.PickedEntryUi(
                id = id,
                name = name,
                path = path,
                kind = fileKindOf(mimeType),
                isDirectory = false,
                detailLabel = sizeLabel,
            )
        }
    }
}
