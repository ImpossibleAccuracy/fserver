package com.fserver.app.data.oneshot

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fserver.app.domain.oneshot.OneShotRepository
import com.fserver.core.oneshot.OneShotTransfersController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/** Accept / decline straight from an offer notification. Accepting writes to the default destination. */
class OneShotOfferReceiver : BroadcastReceiver(), KoinComponent {
    private val controller: OneShotTransfersController by inject()
    private val oneShot: OneShotRepository by inject()
    private val notifications: OneShotNotifications by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val transferId = intent.getStringExtra(ExtraTransferId) ?: return
        val accept = intent.action == ActionAccept
        notifications.dismissOffer(transferId)

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val result = if (accept) {
                    controller.accept(transferId, oneShot.destination.first())
                } else {
                    controller.decline(transferId)
                }
                result.onFailure { Timber.w(it, "Could not answer transfer $transferId") }

                // Started from here while the tap still allows it: bytes follow shortly.
                if (accept && result.isSuccess) OneShotTransferService.start(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ActionAccept = "com.fserver.app.oneshot.ACCEPT"
        private const val ActionDecline = "com.fserver.app.oneshot.DECLINE"
        private const val ExtraTransferId = "transfer_id"

        fun intent(context: Context, transferId: String, accept: Boolean): PendingIntent {
            val action = if (accept) ActionAccept else ActionDecline

            return PendingIntent.getBroadcast(
                context,
                (transferId + action).hashCode(),
                Intent(context, OneShotOfferReceiver::class.java)
                    .setAction(action)
                    .putExtra(ExtraTransferId, transferId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
