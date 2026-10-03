package com.fserver.app.presentation.shared.selection

/** Multi-select over item ids. [active] may stay on with nothing picked, when opened by hand. */
data class Selection<T>(
    val active: Boolean = false,
    val ids: Set<T> = emptySet(),
) {
    /** A long press: selection mode on, [id] picked. */
    fun started(id: T): Selection<T> = Selection(active = true, ids = ids + id)

    /** [closeWhenEmpty] leaves selection mode once the last id is unpicked. */
    fun toggled(id: T, closeWhenEmpty: Boolean = false): Selection<T> {
        val next = if (id in ids) ids - id else ids + id
        return Selection(active = if (closeWhenEmpty) next.isNotEmpty() else active, ids = next)
    }
}
