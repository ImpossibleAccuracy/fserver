package com.fserver.app

import android.app.Application
import com.fserver.app.di.coreModule
import com.fserver.app.di.dataModule
import com.fserver.app.di.domainModule
import com.fserver.app.di.presentationModule
import com.fserver.app.work.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class FServerApplication : Application() {

    /** Lives as long as the process. Android gives `Application` no teardown to cancel it on. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val syncScheduler: SyncScheduler by inject()

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
                domainModule,
                presentationModule,
                coreModule,
            )
        }

        // Here rather than in a ViewModel: the schedule has to survive the UI, and this runs in
        // every process the app is started in, including the one WorkManager wakes.
        syncScheduler.start(appScope)
    }
}
