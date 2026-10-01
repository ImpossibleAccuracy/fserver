package com.fserver.app.data.documents

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Files other apps opened that are being fetched from the peer, by name, in order of asking. */
class DocumentFetches(private val context: Context) {
    private val _active = MutableStateFlow<List<Fetch>>(emptyList())
    val active: StateFlow<List<Fetch>> = _active.asStateFlow()

    /** Runs [block] under [DocumentFetchService], so the fetch keeps its network in the background. */
    suspend fun <T> track(name: String, block: suspend () -> T): T {
        val fetch = Fetch(name)
        _active.update { it + fetch }
        DocumentFetchService.start(context)

        return try {
            block()
        } finally {
            _active.update { it - fetch }
        }
    }

    /** Identity, not equality: two fetches of the same name are two entries. */
    class Fetch(val name: String)
}
