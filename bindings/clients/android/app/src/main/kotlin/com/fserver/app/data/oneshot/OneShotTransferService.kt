package com.fserver.app.data.oneshot

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fserver.core.oneshot.OneShotTransfersController
import com.fserver.core.storage.OneShotTransfersRepository
import com.fserver.core.sync.progress.SyncProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the process up while one-shot transfer bytes move, under a progress notification with a
 * cancel action. Stops itself once nothing has moved for [IdleGrace].
 */
class OneShotTransferService : Service() {
    private val transfers: OneShotTransfersRepository by inject()
    private val progress: SyncProgressRepository by inject()
    private val controller: OneShotTransfersController by inject()
    private val notifications: OneShotNotifications by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stopJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        ServiceCompat.startForeground(
            this,
            OneShotNotifications.ProgressId,
            notifications.progress(emptyList(), emptyList()),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )

        scope.launch {
            combine(transfers.transfers, progress.oneShotTransfers, ::Pair).collect { (all, live) ->
                val moving = live.filterNot { it.isFinished }
                if (moving.isEmpty()) {
                    // Files of one transfer go one after another: a gap between them is not the end.
                    if (stopJob == null) stopJob = scope.launch { delay(IdleGrace); stopSelf() }
                    return@collect
                }

                stopJob?.cancel()
                stopJob = null

                val movingIds = moving.mapTo(mutableSetOf()) { it.transferId }
                val active = all.filter { it.id in movingIds }

                @Suppress("MissingPermission") // a foreground service's own notification
                NotificationManagerCompat.from(this@OneShotTransferService)
                    .notify(OneShotNotifications.ProgressId, notifications.progress(active, live))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionCancel) {
            intent.getStringExtra(ExtraTransferId)?.let { id ->
                scope.launch {
                    controller.cancel(id).onFailure { Timber.w(it, "Could not cancel transfer $id") }
                }
            }
        }
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

    companion object {
        private const val ActionCancel = "com.fserver.app.oneshot.CANCEL"
        private const val ExtraTransferId = "transfer_id"
        private val IdleGrace = 3.seconds

        /** Refused while the app is in the background on API 31+: the transfer then runs without it. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, OneShotTransferService::class.java))
            } catch (e: IllegalStateException) {
                Timber.w(e, "Could not start the transfer service")
            }
        }

        fun cancelIntent(context: Context, transferId: String): PendingIntent = PendingIntent.getService(
            context,
            transferId.hashCode(),
            Intent(context, OneShotTransferService::class.java)
                .setAction(ActionCancel)
                .putExtra(ExtraTransferId, transferId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
