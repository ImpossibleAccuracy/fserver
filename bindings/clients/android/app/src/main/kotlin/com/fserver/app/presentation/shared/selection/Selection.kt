package com.fserver.app.presentation.shared.selection

/** Multi-select over item ids. [active] may stay on with nothing picked, when opened by hand. */
data class Selection(
    val active: Boolean = false,
    val ids: Set<String> = emptySet(),
) {
    /** A long press: selection mode on, [id] picked. */
    fun started(id: String): Selection = Selection(active = true, ids = ids + id)

    /** [closeWhenEmpty] leaves selection mode once the last id is unpicked. */
    fun toggled(id: String, closeWhenEmpty: Boolean = false): Selection {
        val next = if (id in ids) ids - id else ids + id
        return Selection(active = if (closeWhenEmpty) next.isNotEmpty() else active, ids = next)
    }
}
