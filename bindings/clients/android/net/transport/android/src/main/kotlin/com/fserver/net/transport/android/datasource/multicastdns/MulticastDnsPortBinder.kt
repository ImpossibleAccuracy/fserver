package com.fserver.net.transport.android.datasource.multicastdns

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The port the mDNS transport is listening on, published so the advertiser can announce it.
 *
 * Public because both sides live in different packages, but it is one shared object: the transport
 * and the advertiser must be given the *same* instance, or advertising silently never starts.
 */
internal class MulticastDnsPortBinder {
    private val state = MutableStateFlow<Int?>(null)
    val value: Flow<Int?> = state.asStateFlow()

    fun set(port: Int?) {
        state.value = port
    }
}
