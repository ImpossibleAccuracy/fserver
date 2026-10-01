package com.fserver.app.di

import android.app.Application
import com.fserver.app.BuildConfig
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Starts Koin once per process, whoever comes first: a content provider is published before
 * `Application.onCreate` runs, and a call to it may land while that is still loading modules.
 */
object AppGraph {
    @Synchronized
    fun start(application: Application) {
        if (GlobalContext.getOrNull() != null) return

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(application)
            modules(
                dataModule,
                domainModule,
                presentationModule,
                coreModule,
            )
        }
    }
}
