package com.fserver.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.fserver.app.data.AppSettingsStore
import com.fserver.app.data.AuthManagerImpl
import com.fserver.app.data.documents.DocumentFetches
import com.fserver.app.data.documents.DocumentsRepositoryImpl
import com.fserver.app.data.export.ArchiveExporter
import com.fserver.app.data.oneshot.OneShotNotifications
import com.fserver.app.data.oneshot.OneShotNotifier
import com.fserver.app.data.oneshot.OneShotRepositoryImpl
import com.fserver.app.domain.oneshot.OneShotRepository
import com.fserver.app.data.preview.CoilEvictionPreviewer
import com.fserver.app.data.preview.EvictionPreviews
import com.fserver.app.domain.AuthManager
import com.fserver.app.domain.documents.DocumentsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** App-owned state only. Anything the engine persists comes from `:core:storage` - see [coreModule]. */
internal val dataModule = module {
    single { androidContext().dataStore }

    singleOf(::AuthManagerImpl) bind AuthManager::class

    singleOf(::AppSettingsStore)

    // Its auto-accept and notifications run in `AppViewModel`: never for a background job.
    singleOf(::OneShotRepositoryImpl) bind OneShotRepository::class
    single { OneShotNotifications(androidContext()) }
    single { OneShotNotifier(androidContext(), get()) }

    singleOf(::EvictionPreviews)
    singleOf(::CoilEvictionPreviewer)

    singleOf(::DocumentsRepositoryImpl) bind DocumentsRepository::class
    singleOf(::DocumentFetches)

    singleOf(::ArchiveExporter)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fserver_prefs")
