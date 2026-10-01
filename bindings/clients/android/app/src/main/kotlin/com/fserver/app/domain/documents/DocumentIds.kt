package com.fserver.app.domain.documents

/**
 * Document ids: `<sourceId>` for a source's root, `<sourceId>/<path>` below it. Source ids are
 * uuids, so the first `/` always ends one.
 */
object DocumentIds {
    fun of(sourceId: String, path: String): String = if (path.isEmpty()) sourceId else "$sourceId/$path"

    fun sourceOf(documentId: String): String = documentId.substringBefore('/')

    /** Source-relative path, empty for the source's root. */
    fun pathOf(documentId: String): String = documentId.substringAfter('/', missingDelimiterValue = "")

    fun isChild(parentId: String, documentId: String): Boolean {
        if (sourceOf(parentId) != sourceOf(documentId) || parentId == documentId) return false

        val parent = pathOf(parentId)
        return parent.isEmpty() || pathOf(documentId).startsWith("$parent/")
    }
}