package com.fserver.app.di

import com.fserver.app.data.work.SyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

internal val domainModule = module {
    // `:core` keeps no timer of its own, so the periodic pass is the host's to schedule.
    single { SyncScheduler(context = androidContext(), sources = get()) }
}
