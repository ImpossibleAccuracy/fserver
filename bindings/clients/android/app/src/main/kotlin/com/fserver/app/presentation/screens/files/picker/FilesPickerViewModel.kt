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
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.SelectedEntry
import com.fserver.app.data.SendSelectionStore
import com.fserver.app.presentation.model.FileKindUi
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerIntent
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState.MediaGrouping
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerState.PickerSource
import com.fserver.app.presentation.screens.files.picker.model.FilesPickerUiEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.uuid.Uuid

/** Newest media first, capped — the selection list renders every row eagerly. */
private const val MEDIA_SCAN_LIMIT = 500

class FilesPickerViewModel(
    @SuppressLint("StaticFieldLeak") private val context: Context,
    private val selectionStore: SendSelectionStore,
) : ViewModel() {
    private val effects = Channel<FilesPickerUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    private val editable = MutableStateFlow(Editable())

    val state: StateFlow<FilesPickerState> = editable
        .map { it.toPresentation() }
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

            FilesPickerIntent.SourceClosed ->
                editable.update { it.copy(activeSource = null) }

            FilesPickerIntent.DoneClicked -> {
                val id = selectionStore.put(editable.value.toSelection())
                viewModelScope.launch {
                    effects.send(FilesPickerUiEffect.SelectionReady(id))
                }
            }

            is FilesPickerIntent.DirectoryExpansionToggled -> toggleDirectory(intent.id)

            is FilesPickerIntent.DirectorySelectionToggled ->
                editable.update {
                    it.copy(selectedDirs = it.selectedDirs.toggled(intent.id))
                }

            is FilesPickerIntent.MediaGroupingSelected ->
                editable.update {
                    // The tab set is rebuilt from scratch, so the old pick no longer names a tab.
                    it.copy(mediaGrouping = intent.grouping, activeMediaTabId = null)
                }

            is FilesPickerIntent.MediaTabSelected ->
                editable.update { it.copy(activeMediaTabId = intent.id) }

            is FilesPickerIntent.MediaSelectionToggled ->
                editable.update {
                    it.copy(selectedMedia = it.selectedMedia.toggled(intent.id))
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
                    activeSource = PickerSource.StorageAccessFramework,
                    documents = state.documents.mergedWith(entries),
                )
            }
        } catch (t: Throwable) {
            Timber.e(t, "Error handling URI: $uri")
        }
    }

    /**
     * Opens the full-access tree at the storage volumes. Only the roots are expanded; every
     * level below is listed on demand, so granting access does not walk the whole device.
     */
    private suspend fun scanRoots() {
        val roots = withContext(Dispatchers.IO) {
            storageRoots().map { it.toDirNode() }
        }

        editable.update {
            it.copy(
                activeSource = PickerSource.FullAccess,
                treeRoots = roots,
                expandedDirs = it.expandedDirs + roots.map { root -> root.path },
            )
        }

        roots.forEach { loadChildren(it.path) }
    }

    private fun toggleDirectory(path: String) {
        val current = editable.value
        val expanding = path !in current.expandedDirs

        editable.update { it.copy(expandedDirs = it.expandedDirs.toggled(path)) }

        if (expanding && path !in current.treeChildren) {
            viewModelScope.launch { loadChildren(path) }
        }
    }

    private suspend fun loadChildren(path: String) {
        val children = withContext(Dispatchers.IO) {
            File(path).listDirectories()
        }

        editable.update {
            it.copy(treeChildren = it.treeChildren + (path to children))
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
            Timber.e(t, "Error querying MediaStore")
            return@withContext
        }

        editable.update {
            it.copy(
                activeSource = PickerSource.MediaStore,
                media = it.media.mergedWith(media),
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
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
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
                    val bucketIndex = cursor.getColumnIndexOrThrow(
                        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                    )

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
                                bucket = cursor.getString(bucketIndex),
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
private fun <T : Editable.Entry> List<T>.mergedWith(new: List<T>): List<T> {
    val known = mapTo(mutableSetOf()) { it.path }
    return this + new.filterNot { it.path in known }
}

private fun Editable.Entry.toSelectedEntry(): SelectedEntry {
    val ui = toPresentation()
    return SelectedEntry(
        id = ui.id,
        name = ui.name,
        source = ui.path,
        isDirectory = ui.isDirectory,
    )
}

private fun <T> Set<T>.toggled(value: T): Set<T> =
    if (value in this) this - value else this + value

private fun fileKindOf(mimeType: String?): FileKindUi = when {
    mimeType == null -> FileKindUi.Other
    mimeType.startsWith("image/") -> FileKindUi.Image
    mimeType.startsWith("video/") -> FileKindUi.Video
    mimeType.startsWith("audio/") -> FileKindUi.Audio
    else -> FileKindUi.Document
}

private fun extensionLabelOf(name: String): String? =
    name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.uppercase()

/** One `listFiles` per directory: the count and "has sub-folders" both come out of it. */
private fun File.listDirectories(): List<Editable.DirNode> =
    listFiles()
        ?.filter { it.isDirectory }
        ?.sortedBy { it.name.lowercase() }
        ?.map { it.toDirNode() }
        ?: emptyList()

private fun File.toDirNode(): Editable.DirNode {
    val children = listFiles()
    return Editable.DirNode(
        path = absolutePath,
        name = name.ifEmpty { absolutePath },
        itemCount = children?.size ?: 0,
        hasChildDirs = children?.any { it.isDirectory } == true,
    )
}

private data class Editable(
    val sources: List<PickerSource> = emptyList(),
    val activeSource: PickerSource? = null,
    val isLoading: Boolean = false,
    val documents: List<Entry> = emptyList(),
    val treeRoots: List<DirNode> = emptyList(),
    val treeChildren: Map<String, List<DirNode>> = emptyMap(),
    val expandedDirs: Set<String> = emptySet(),
    val selectedDirs: Set<String> = emptySet(),
    val media: List<Entry.Media> = emptyList(),
    val mediaGrouping: MediaGrouping = MediaGrouping.Type,
    val selectedMedia: Set<String> = emptySet(),
    val activeMediaTabId: String? = null,
) {
    fun toPresentation() = FilesPickerState(
        sources = sources,
        activeSource = activeSource,
        isLoading = isLoading,
        entries = documents.map { it.toPresentation() },
        tree = flattenTree(),
        mediaGrouping = mediaGrouping,
        mediaTabs = mediaTabs(),
        activeMediaTabId = activeMediaTabId,
        // Everything a SAF pick produced counts as selected the moment it is parsed; the
        // other two sources contribute only what the user ticked.
        selectedCount = documents.size + selectedDirs.size + selectedMedia.size,
    )

    /**
     * The three sources flattened into one list, in the order they are shown: what the system
     * picker handed over, the ticked directories, then the ticked media.
     */
    fun toSelection(): List<SelectedEntry> = buildList {
        documents.forEach { add(it.toSelectedEntry()) }

        val dirsByPath = (treeRoots + treeChildren.values.flatten()).associateBy { it.path }
        selectedDirs.forEach { path ->
            add(
                SelectedEntry(
                    id = path,
                    name = dirsByPath[path]?.name ?: path,
                    source = path,
                    isDirectory = true,
                )
            )
        }

        media.filter { it.id in selectedMedia }.forEach { add(it.toSelectedEntry()) }
    }

    /** Depth-first walk of the loaded tree, stopping at every collapsed branch. */
    private fun flattenTree(): List<FilesPickerState.TreeNodeUi> = buildList {
        fun append(node: DirNode, depth: Int) {
            val expanded = node.path in expandedDirs
            add(
                FilesPickerState.TreeNodeUi(
                    id = node.path,
                    name = node.name,
                    depth = depth,
                    expandable = node.hasChildDirs,
                    expanded = expanded,
                    selected = node.path in selectedDirs,
                    detailLabel = "${node.itemCount} items",
                )
            )
            if (expanded) {
                treeChildren[node.path]?.forEach { append(it, depth + 1) }
            }
        }

        treeRoots.forEach { append(it, 0) }
    }

    private fun mediaTabs(): List<FilesPickerState.MediaTabUi> {
        if (media.isEmpty()) return emptyList()

        return when (mediaGrouping) {
            MediaGrouping.Type -> media
                .groupBy { fileKindOf(it.mimeType) }
                .toList()
                .sortedBy { (kind, _) -> MediaKindOrder.indexOf(kind) }
                .map { (kind, items) ->
                    FilesPickerState.MediaTabUi(
                        id = "kind-$kind",
                        kind = kind,
                        title = kind.name,
                        items = items.map { it.toMediaItem() },
                    )
                }

            MediaGrouping.Directory -> media
                .groupBy { it.bucket ?: "Other" }
                .toList()
                .sortedBy { (bucket, _) -> bucket.lowercase() }
                .map { (bucket, items) ->
                    FilesPickerState.MediaTabUi(
                        id = "bucket-$bucket",
                        kind = null,
                        title = bucket,
                        items = items.map { it.toMediaItem() },
                    )
                }
        }
    }

    private fun Entry.Media.toMediaItem(): FilesPickerState.MediaItemUi {
        val kind = fileKindOf(mimeType)
        return FilesPickerState.MediaItemUi(
            id = id,
            name = name,
            kind = kind,
            selected = id in selectedMedia,
            detailLabel = sizeLabel,
            // Only the non-visual tiles carry a label; a grid tile shows its kind instead.
            extensionLabel = extensionLabelOf(name).takeIf {
                kind != FileKindUi.Image && kind != FileKindUi.Video
            },
        )
    }

    /** A directory in the full-access tree, listed once when its parent is expanded. */
    data class DirNode(
        val path: String,
        val name: String,
        val itemCount: Int,
        val hasChildDirs: Boolean,
    )

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
            val bucket: String?,
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

/** Tab order for type grouping; anything unlisted sorts last. */
private val MediaKindOrder = listOf(
    FileKindUi.Image,
    FileKindUi.Video,
    FileKindUi.Audio,
    FileKindUi.Document,
    FileKindUi.Other,
)
