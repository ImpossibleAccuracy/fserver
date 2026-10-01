package com.fserver.app.data.documents

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fserver.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Keeps the network up while a file another app opened is fetched from the peer: without a
 * foreground service, Doze and battery saver cut a background process off. Stops once none is left.
 */
class DocumentFetchService : Service() {
    private val fetches: DocumentFetches by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** By id, so a start that lands while the last fetch ends is not stopped with it. */
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()

        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(Channel, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.documents_channel_fetch))
                .build(),
        )
        ServiceCompat.startForeground(
            this,
            NotificationId,
            notification(fetches.active.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )

        scope.launch {
            fetches.active.collect { active ->
                if (active.isEmpty()) return@collect run { stopSelf(lastStartId) }

                @Suppress("MissingPermission") // a foreground service's own notification
                NotificationManagerCompat.from(this@DocumentFetchService).notify(NotificationId, notification(active))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Started after its fetch already ended: nothing to hold the process for.
        if (fetches.active.value.isEmpty()) stopSelf(startId)
        return START_NOT_STICKY
    }

    // API 35+ caps a data sync service at 6 hours a day.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(active: List<DocumentFetches.Fetch>) =
        NotificationCompat.Builder(this, Channel)
            .setSmallIcon(R.drawable.ic_notification_transfer)
            .setContentTitle(
                active.singleOrNull()?.let { getString(R.string.documents_notification_fetching, it.name) }
                    ?: resources.getQuantityString(R.plurals.documents_notification_fetching_many, active.size, active.size),
            )
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    companion object {
        private const val Channel = "documents_fetch"
        private const val NotificationId = 0x0D0C

        /** Refused while the app may not start one from the background (API 31+): the fetch then runs without it. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DocumentFetchService::class.java))
            } catch (e: IllegalStateException) {
                Timber.w(e, "Could not start the document fetch service")
            }
        }
    }
}
