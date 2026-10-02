package com.fserver.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.video.VideoFrameDecoder
import com.fserver.app.presentation.shared.viewer.impl.AudioArtwork
import com.fserver.app.presentation.shared.viewer.impl.AudioArtworkFetcher
import com.fserver.app.presentation.shared.viewer.impl.FileImageMapper
import com.fserver.app.data.preview.EvictionPreviews
import com.fserver.app.di.AppGraph
import com.fserver.app.data.work.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.android.inject
import timber.log.Timber

class FServerApplication : Application(), SingletonImageLoader.Factory {

    /** Lives as long as the process. Android gives `Application` no teardown to cancel it on. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val syncScheduler: SyncScheduler by inject()
    private val evictionPreviews: EvictionPreviews by inject()

    override fun onCreate() {
        super.onCreate()

        // Debug builds only: release logging would leak device addresses and peer identifiers.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        AppGraph.start(this)

        // Here rather than in a ViewModel: the schedule has to survive the UI, and this runs in
        // every process the app is started in, including the one WorkManager wakes.
        syncScheduler.start(appScope)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            // Under the cache directory by name, so the storage screen counts it as cache.
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(ImageCacheDirectory).toOkioPath())
                    .maxSizePercent(ImageCacheShare)
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
                add(AudioArtworkFetcher.Factory(), AudioArtwork::class)
                add(FileImageMapper(evictionPreviews))
            }
            .build()

    private companion object {
        const val ImageCacheDirectory = "image_cache"
        const val ImageCacheShare = 0.02
    }
}
