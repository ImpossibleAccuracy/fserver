package com.fserver.app.data.storage

import com.fserver.core.network.auth.OfferedAuthMethod
import com.fserver.core.store.AuthSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AuthSettingsStoreImpl : AuthSettingsStore {
    private val _offeredMethods = MutableStateFlow(
        listOf(
            // TODO: development only values - not persisted, and the password is a placeholder
            OfferedAuthMethod.ConfirmFingerprint,
            OfferedAuthMethod.Password("ABCD"),
        )
    )
    override val offeredMethods: StateFlow<List<OfferedAuthMethod>> = _offeredMethods.asStateFlow()
}
