package com.fserver.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.fserver.app.data.AppSettingsStore
import com.fserver.app.data.AuthManagerImpl
import com.fserver.app.data.SendSelectionStore
import com.fserver.app.domain.AuthManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** App-owned state only. Anything the engine persists comes from `:core:storage` - see [coreModule]. */
internal val dataModule = module {
    single { androidContext().dataStore }

    singleOf(::AuthManagerImpl) bind AuthManager::class

    singleOf(::SendSelectionStore)
    singleOf(::AppSettingsStore)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fserver_prefs")
