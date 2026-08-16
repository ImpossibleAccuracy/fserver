package com.fserver.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.fserver.app.data.AuthSettingsStoreImpl
import com.fserver.app.data.LocalIdentityStoreImpl
import com.fserver.core.domain.store.AuthSettingsStore
import com.fserver.core.domain.store.LocalIdentityStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal val dataModule = module {
    single { androidContext().dataStore }

    singleOf(::LocalIdentityStoreImpl) bind LocalIdentityStore::class
    singleOf(::AuthSettingsStoreImpl) bind AuthSettingsStore::class
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fserver_prefs")
