package com.fserver.app.presentation.shared.browser.model

import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.fileKindOf
import com.fserver.core.files.SyncFileEntry

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

/** Flattens a list of [SyncFileEntry] into a list of [FileBrowserUi.File] for a given [directory]. */
fun List<SyncFileEntry>.toFlatPreview(
    directory: String = "/",
): List<FileBrowserUi.File> {
    val allFiles = associateWith { it.directory }

    val allDirectories = allFiles.values
        .flatMap { dir ->
            dir ?: return@flatMap listOf("/")

            dir.split('/')
                .filter { it.isNotEmpty() }
                .runningFold("") { acc, part ->
                    "$acc/$part".normalizeDirectory()
                }
        }
        .filter { it.isNotEmpty() }
        .toSet()

    //Remark: directories and files are filtered separately
    // Normally, we should create full virtual directory structure
    // But it costs too much time and memory, so we just filter files and directories separately

    val foundFiles = allFiles.filter { (_, fileDirectory) ->
        isDirectoriesMatching(fileDirectory, directory)
    }

    val foundDirectories = allDirectories.filter { path ->
        if (path == directory) return@filter false

        // Find only the immediate subdirectories of the current directory
        val substring = path.substringAfter(directory, missingDelimiterValue = "")
        if (substring.isBlank()) return@filter false

        // Check if the substring contains any additional slashes, indicating it's a subdirectory
        !substring.drop(1).contains("/")
    }

    return buildList(foundFiles.size + foundDirectories.size) {
        for (directory in foundDirectories) {
            this += FileBrowserUi.File(
                id = directory,
                path = directory,
                name = directoryName(directory),
                kind = FileKindUi.Folder,
                locator = null,
                size = null,
                extensionLabel = null,
            )
        }

        for (entry in foundFiles.keys) {
            this += entry.asPreviewFile()
        }
    }
}

fun SyncFileEntry.asPreviewFile(): FileBrowserUi.File {
    val name = path.substringAfterLast('/')
    val kind = fileKindOf(name)
    val extension = name.substringAfterLast(".", missingDelimiterValue = "")

    return FileBrowserUi.File(
        id = fileId,
        path = path,
        name = name,
        kind = kind,
        locator = locator,
        size = size,
        modifiedAt = modifiedAt,
        location = when {
            isRemote -> FileBrowserUi.File.Location.Remote
            else -> FileBrowserUi.File.Location.Local
        },
        extensionLabel = when {
            kind.isMedia -> null
            else -> extension.ifEmpty { "?" }.uppercase()
        },
    )
}

fun directoryName(directory: String): String = directory.substringAfterLast("/")

private val SyncFileEntry.directory: String?
    get() = path.substringBeforeLast('/', missingDelimiterValue = "")
        .takeIf { it.isNotEmpty() }
        ?.normalizeDirectory()

private fun isDirectoriesMatching(dir1: String?, dir2: String?): Boolean {
    val normalized1 = dir1.normalizeDirectory()
    val normalized2 = dir2.normalizeDirectory()

    return normalized1 == normalized2
}

private fun String?.normalizeDirectory(): String =
    this
        ?.let { if (it.startsWith("/")) it else "/$it" }
        ?.let { if (it.endsWith("/")) it.dropLast(1) else it }
        ?.takeUnless { it.isEmpty() } ?: "/"
