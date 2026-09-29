package com.fserver.app.presentation.shared.browser.model

import com.fserver.app.presentation.composable.model.fileName
import com.fserver.app.presentation.composable.model.fileKindOf
import com.fserver.common.model.FileSize
import com.fserver.core.files.SyncFileEntry
import com.fserver.core.sync.index.LocalIndexedFile

/** Returns true if the given [files] contain a majority of media files. */
fun isMediaCollection(files: List<FileBrowserUi.PreviewContentEntry>): Boolean {
    val media = files.count {
        when (it) {
            is FileBrowserUi.Directory -> false
            is FileBrowserUi.File -> it.kind.isMedia
        }
    }

    val isMediaCollection = media.toDouble() / files.size > FileBrowserUi.MediaRatioThreshold
    return isMediaCollection
}

/**
 * Folds entries into a tree by their `/`-separated paths, each level sorted by [sort] with
 * folders first. Directory sizes roll up their whole subtree.
 */
fun List<SyncFileEntry>.toTree(
    sort: FileSortUi = FileSortUi.Name,
    ascending: Boolean = true,
    fileOf: (SyncFileEntry) -> FileBrowserUi.File = { it.asPreviewFile() },
): FileBrowserUi.Tree {
    val root = EntryNode(path = "")
    for (entry in this) {
        val segments = entry.path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) continue
        root.add(segments, entry)
    }

    return FileBrowserUi.Tree(
        directories = root.contentsUi(sort, ascending, fileOf),
    )
}

private class EntryNode(val path: String) {
    val children = LinkedHashMap<String, EntryNode>()
    val files = mutableListOf<SyncFileEntry>()
    var bytes: Long = 0

    fun add(segments: List<String>, entry: SyncFileEntry) {
        bytes += entry.size.bytes

        if (segments.size == 1) {
            files += entry
            return
        }

        val head = segments.first()
        children.getOrPut(head) { EntryNode("$path/$head") }.add(segments.drop(1), entry)
    }

    fun contentsUi(
        sort: FileSortUi,
        ascending: Boolean,
        fileOf: (SyncFileEntry) -> FileBrowserUi.File,
    ): List<FileBrowserUi.PreviewContentEntry> {
        val directories = children.map { (name, node) ->
            FileBrowserUi.Directory(
                path = node.path,
                name = name,
                files = node.children.size + node.files.size,
                size = FileSize(node.bytes),
                contents = node.contentsUi(sort, ascending, fileOf),
            )
        }

        return directories.sortedWith(directoryComparator(sort, ascending)) +
                files.map(fileOf).sortedWith(fileComparator(sort, ascending))
    }
}

private fun directoryComparator(
    sort: FileSortUi,
    ascending: Boolean,
): Comparator<FileBrowserUi.Directory> {
    val comparator = when (sort) {
        FileSortUi.Size -> compareBy<FileBrowserUi.Directory> { it.size.bytes }
        else -> compareBy { it.name.lowercase() }
    }
    return if (ascending) comparator else comparator.reversed()
}

private fun fileComparator(sort: FileSortUi, ascending: Boolean): Comparator<FileBrowserUi.File> {
    val comparator = when (sort) {
        FileSortUi.Name -> compareBy { it.name.lowercase() }
        FileSortUi.Date -> compareBy { it.modifiedAt }
        FileSortUi.Size -> compareBy { it.size?.bytes }
        FileSortUi.Kind -> compareBy<FileBrowserUi.File> { it.kind }
            .thenBy { it.extensionLabel.orEmpty() }
            .thenBy { it.name.lowercase() }
    }
    return if (ascending) comparator else comparator.reversed()
}

fun SyncFileEntry.asPreviewFile(): FileBrowserUi.File {
    val name = path.fileName()
    val kind = fileKindOf(name)
    val extension = name.substringAfterLast(".", missingDelimiterValue = "")

    return FileBrowserUi.File(
        id = fileId,
        sourceId = sourceId,
        path = path,
        name = name,
        kind = kind,
        locator = locator,
        size = size,
        modifiedAt = modifiedAt,
        locations = locations,
        extensionLabel = when {
            kind.isMedia -> null
            else -> extension.ifEmpty { "?" }.uppercase()
        },
    )
}

/** Sides holding the bytes right now. An evicted copy is known but not held, so it is not listed. */
val SyncFileEntry.locations: Set<FileBrowserUi.File.Location>
    get() = buildSet {
        if (localState is LocalIndexedFile.State.Present) add(FileBrowserUi.File.Location.Local)
        if (remoteState is LocalIndexedFile.State.Present) add(FileBrowserUi.File.Location.Remote)
    }
