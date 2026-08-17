package com.fserver.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.fserver.app.data.AppSettingsStore
import com.fserver.app.data.AuthManagerImpl
import com.fserver.app.data.SavedDevicesRepositoryImpl
import com.fserver.app.data.SendSelectionStore
import com.fserver.app.data.storage.ServerGeneralStorage
import com.fserver.app.domain.AuthManager
import com.fserver.app.domain.SavedDevicesRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal val dataModule = module {
    single { androidContext().dataStore }

    singleOf(::SavedDevicesRepositoryImpl) bind SavedDevicesRepository::class

    singleOf(::ServerGeneralStorage)
    singleOf(::SendSelectionStore)
    singleOf(::AppSettingsStore)
    singleOf(::AuthManagerImpl) bind AuthManager::class

    // The stores `:core` was handed, republished so a ViewModel can read what the engine reads
    // without learning that they came in through `FServerConfig`.
    single { get<ServerGeneralStorage>().auth }
    single { get<ServerGeneralStorage>().trust }
    single { get<ServerGeneralStorage>().identity }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fserver_prefs")
