package com.fserver.app.domain.documents

import com.fserver.core.files.SyncFileEntry

sealed interface DocumentNode {
    val path: String

    val name: String
        get() = path.substringAfterLast('/')

    /** Implied by the files below it: the index holds no folders. */
    data class Folder(override val path: String) : DocumentNode

    data class File(val entry: SyncFileEntry) : DocumentNode {
        override val path: String get() = entry.path
    }
}

/** Direct children of folder [path] (empty for the root) among one source's [entries]. */
fun childrenOf(path: String, entries: List<SyncFileEntry>): List<DocumentNode> {
    val prefix = if (path.isEmpty()) "" else "$path/"
    val below = entries.filter { it.path.startsWith(prefix) }

    val (files, nested) = below.partition { '/' !in it.path.removePrefix(prefix) }
    val folders = nested
        .mapTo(linkedSetOf()) { prefix + it.path.removePrefix(prefix).substringBefore('/') }
        .map { DocumentNode.Folder(it) }

    return folders + files.map { DocumentNode.File(it) }
}

/** The node at [path] among one source's [entries], or null when nothing is there. */
fun nodeAt(path: String, entries: List<SyncFileEntry>): DocumentNode? {
    if (path.isEmpty()) return DocumentNode.Folder(path)

    entries.firstOrNull { it.path == path }?.let { return DocumentNode.File(it) }
    return DocumentNode.Folder(path).takeIf { entries.any { it.path.startsWith("$path/") } }
}
