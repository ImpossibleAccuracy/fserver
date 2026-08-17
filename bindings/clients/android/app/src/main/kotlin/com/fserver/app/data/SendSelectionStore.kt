package com.fserver.app.data

import kotlin.uuid.Uuid

/**
 * Hand-off for what the picker selected, keyed by a short id that travels as a navigation
 * argument instead of the selection itself.
 *
 * A selection has no size limit, so putting it in the back stack is not an option: nav keys are
 * serialized into saved state on every configuration change and process save. The id is, and the
 * items stay here.
 *
 * In memory only. Process death drops the selection, and the screen holding its id renders as
 * "nothing to send" rather than sending something the user no longer sees.
 */
class SendSelectionStore {
    private val selections = mutableMapOf<String, List<SelectedEntry>>()

    /** Stores [items] under a fresh id and returns it. */
    fun put(items: List<SelectedEntry>): String {
        val id = Uuid.random().toString()
        selections[id] = items
        return id
    }

    fun selection(id: String): List<SelectedEntry>? = selections[id]

    fun clear(id: String) {
        selections.remove(id)
    }
}

/**
 * One thing the user picked, in whichever shape its source produced it. [source] is a
 * `content://` URI for the system picker and MediaStore, and a filesystem path for full access —
 * enough to identify the entry; resolving it to bytes is the transfer's job.
 */
data class SelectedEntry(
    val id: String,
    val name: String,
    val source: String,
    val isDirectory: Boolean,
)
