package com.fserver.app.presentation.shared.browser.model

import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.fileExtension
import com.fserver.app.presentation.composable.model.fileKindOf
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.scan.ScannedFile

fun List<ScannedFile>.toPreview(
    kind: SourceKindUi,
    volumes: List<SourceLocation.Root.Volume> = emptyList(),
): FileBrowserUi = when (kind) {
    SourceKindUi.Media -> FileBrowserUi.Gallery(
        files = sortedByDescending { it.lastModified }
            .map { it.toPreviewFile() },
    )

    SourceKindUi.Folder,
    SourceKindUi.AppStorage -> FileBrowserUi.PlainList(
        files = sortedByDescending { it.lastModified }
            .map { it.toPreviewFile() },
    )

    SourceKindUi.WholeDevice -> FileBrowserUi.Tree(
        directories = toDirectoryTree(volumes),
    )
}

private fun ScannedFile.toPreviewFile(): FileBrowserUi.File {
    val name = path.substringAfterLast('/')
    val extension = name.fileExtension
    val kind = fileKindOf(name)

    return FileBrowserUi.File(
        id = locator,
        path = path,
        name = name,
        kind = kind,
        locator = locator,
        size = size,
        extensionLabel = when (kind) {
            FileKindUi.Image, FileKindUi.Video -> null
            else -> extension.ifEmpty { "?" }.uppercase()
        },
    )
}

/**
 * The folders the walk put files in, as one tree per storage volume.
 *
 * Nesting is read off the locator rather than the canonical path: what the user picks here is
 * re-scanned as a [SourceLocation.Directory], which is addressed by device path. The canonical
 * path is used only to tell which volume a file came off, since that is the one thing an
 * absolute path does not say on its own.
 */
private fun List<ScannedFile>.toDirectoryTree(
    volumes: List<SourceLocation.Root.Volume>,
): List<FileBrowserUi.Directory> {
    if (isEmpty()) return emptyList()

    val mounts = volumes.associate { it.id to it.path }

    return groupBy { it.path.substringBefore('/') }
        .mapNotNull { (volumeId, files) ->
            val mount = mounts[volumeId] ?: commonDirectoryPrefix(files)
            val root = DirectoryNode(label = volumeId)

            for (file in files) {
                root.add(
                    segments = file.locator
                        .substringBeforeLast('/', missingDelimiterValue = "")
                        .removePrefix(mount)
                        .split('/')
                        .filter { it.isNotEmpty() },
                    file = file,
                )
            }

            root.takeIf { it.files > 0 }?.toUi(path = mount, isVolume = true)
        }
        .sortedByDescending { it.size.bytes }
}

/** Longest directory both ends of the scan agree on — the stand-in for an unknown mount point. */
private fun commonDirectoryPrefix(files: List<ScannedFile>): String {
    val directories = files.map { it.locator.substringBeforeLast('/', missingDelimiterValue = "") }
    val first = directories.first().split('/')

    var shared = first.size
    for (directory in directories) {
        val segments = directory.split('/')
        var index = 0
        while (index < shared && index < segments.size && segments[index] == first[index]) index++
        shared = index
    }

    return first.take(shared).joinToString(separator = "/")
}

/**
 * Mutable trie the tree is folded up from.
 *
 * Counts roll up, so a parent carries its whole subtree, while [contents] stays what the folder
 * itself holds. Scanned files are kept by reference and only turned into UI models for the few
 * that survive the per-level cap — a whole-device walk finds far more than any tree shows.
 */
private class DirectoryNode(val label: String) {
    val children = LinkedHashMap<String, DirectoryNode>()
    val contents = mutableListOf<ScannedFile>()
    var files: Int = 0
    var bytes: Long = 0

    fun add(segments: List<String>, file: ScannedFile) {
        files++
        bytes += file.size.bytes

        val head = segments.firstOrNull()
        if (head == null) {
            contents += file
            return
        }

        children.getOrPut(head) { DirectoryNode(head) }.add(segments.drop(1), file)
    }

    fun toUi(path: String, isVolume: Boolean = false): FileBrowserUi.Directory {
        val directories = children.values
            .sortedByDescending { it.bytes }
            .map { it.toUi(path = "$path/${it.label}") }

        val files = contents
            .sortedByDescending { it.lastModified }
            .map { it.toPreviewFile() }

        val contents = directories.plus(files)

        return FileBrowserUi.Directory(
            path = path,
            name = label,
            files = contents.size,
            size = FileSize(bytes),
            isVolume = isVolume,
            contents = contents,
        )
    }
}
