package com.fserver.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fserver.app.domain.AuthManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthManagerImpl(
    private val dataStore: DataStore<Preferences>,
) : AuthManager {
    override val profile: Flow<AuthManager.Profile?> = dataStore.data.map {
        val name = it[DEVICE_NAME] ?: return@map null

        AuthManager.Profile(
            name = name
        )
    }

    override suspend fun login(name: String) {
        dataStore.edit {
            it[DEVICE_NAME] = name
        }
    }

    override suspend fun ensureLoggedIn() {
        login(TESTING_DEVICE_NAME)
    }

    private companion object {
        const val TESTING_DEVICE_NAME = "TEST DEVICE"

        val DEVICE_NAME = stringPreferencesKey("device_name")
    }
}
