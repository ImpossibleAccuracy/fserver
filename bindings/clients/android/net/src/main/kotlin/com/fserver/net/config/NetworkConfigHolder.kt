package com.fserver.net.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Live-swappable [NetworkConfig]. Collaborators under `:net` read [current] per use instead of
 * capturing a copy at construction, so [reload] takes effect without rebuilding them.
 */
internal class NetworkConfigHolder<M : Any>(initial: NetworkConfig<M>) {
    private val state = MutableStateFlow(initial)
    private val reactors = CopyOnWriteArrayList<ConfigAware>()
    private val lock = Mutex()

    val flow: StateFlow<NetworkConfig<M>> = state.asStateFlow()

    val current: NetworkConfig<M> get() = state.value

    fun register(reactor: ConfigAware) {
        reactors += reactor
    }

    suspend fun reload(new: NetworkConfig<M>): Unit = lock.withLock {
        val old = state.value
        if (old === new) return@withLock
        // Published first, so anything starting from here on is already under the new config and
        // only what predates the swap is left for the reactors.
        state.value = new
        reactors.forEach { it.onConfigChanged(old, new) }
    }
}

/**
 * Collaborator holding something the previous config created and the new one may not allow.
 */
internal fun interface ConfigAware {
    suspend fun onConfigChanged(old: NetworkConfig<*>, new: NetworkConfig<*>)
}
