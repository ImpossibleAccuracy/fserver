package com.fserver.app.data.oneshot

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fserver.app.R
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import com.fserver.core.sync.progress.OneShotFileTransfer

/** Notifications of one-shot transfers: the foreground one while bytes move, and one per offer. */
class OneShotNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    // Idempotent, and cheap enough to run with the first use.
    init {
        createChannels()
    }

    private fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(ProgressChannel, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.oneshot_channel_progress))
                    .build(),
                NotificationChannelCompat.Builder(OffersChannel, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.oneshot_channel_offers))
                    .build(),
            )
        )
    }

    /** What [active] transfers are doing; [live] is what `:core` reports on the move. */
    fun progress(active: List<OneShotTransfer>, live: List<OneShotFileTransfer>): Notification {
        val single = active.singleOrNull()
        val liveByFile = live.associateBy { it.transferId to it.index }

        var total = 0L
        var done = 0L
        active.forEach { transfer ->
            transfer.files.forEach { file ->
                total += file.size
                done += when {
                    file.status == OneShotTransferFile.Status.Completed -> file.size
                    else -> liveByFile[transfer.id to file.index]?.transferredBytes ?: 0L
                }
            }
        }

        val title = when {
            single == null -> context.resources.getQuantityString(
                R.plurals.oneshot_notification_many, active.size, active.size,
            )

            single.direction is OneShotTransfer.Direction.Outgoing ->
                context.getString(R.string.oneshot_notification_sending, single.peer.displayName)

            else -> context.getString(R.string.oneshot_notification_receiving, single.peer.displayName)
        }

        val builder = NotificationCompat.Builder(context, ProgressChannel)
            .setSmallIcon(R.drawable.ic_notification_transfer)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (total > 0) {
            builder
                .setContentText(
                    context.getString(R.string.oneshot_notification_progress, formatted(done), formatted(total)),
                )
                .setProgress(PercentMax, (done * PercentMax / total).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }

        if (single != null) {
            builder.addAction(
                0,
                context.getString(R.string.action_cancel),
                OneShotTransferService.cancelIntent(context, single.id),
            )
        }

        return builder.build()
    }

    fun showOffer(transfer: OneShotTransfer) {
        if (!canNotify()) return

        val count = transfer.files.size
        val notification = NotificationCompat.Builder(context, OffersChannel)
            .setSmallIcon(R.drawable.ic_notification_transfer)
            .setContentTitle(context.resources.getQuantityString(R.plurals.incoming_title, count, count))
            .setContentText(
                context.getString(
                    R.string.incoming_from,
                    transfer.peer.displayName,
                    formatted(transfer.files.sumOf { it.size }),
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.action_decline), OneShotOfferReceiver.intent(context, transfer.id, accept = false))
            .addAction(0, context.getString(R.string.action_accept), OneShotOfferReceiver.intent(context, transfer.id, accept = true))
            .build()

        @Suppress("MissingPermission") // canNotify
        manager.notify(transfer.id, OfferId, notification)
    }

    fun dismissOffer(transferId: String) = manager.cancel(transferId, OfferId)

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        }

    private fun formatted(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    companion object {
        const val ProgressId = 0x05E7
        private const val OfferId = 0x0FFE
        private const val PercentMax = 100
        private const val ProgressChannel = "oneshot_progress"
        private const val OffersChannel = "oneshot_offers"
    }
}
