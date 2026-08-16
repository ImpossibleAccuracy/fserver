package com.fserver.app

import android.app.Application
import com.fserver.app.di.coreModule
import com.fserver.app.di.dataModule
import com.fserver.app.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class FServerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Debug builds only: release logging would leak device addresses and peer identifiers.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@FServerApplication)
            modules(
                dataModule,
                presentationModule,
                coreModule,
            )
        }
    }
}
