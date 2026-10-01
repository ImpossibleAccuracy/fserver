package com.fserver.app.data.oneshot

import android.content.Context
import com.fserver.core.oneshot.model.OneShotTransfer

/** What the user sees of one-shot transfers outside the app's screens. */
class OneShotNotifier(
    private val context: Context,
    private val notifications: OneShotNotifications,
) {
    /** Keeps the process up under a progress notification while bytes move. May be refused. */
    fun startTransferService() = OneShotTransferService.start(context)

    fun showOffer(transfer: OneShotTransfer) = notifications.showOffer(transfer)

    fun dismissOffer(transferId: String) = notifications.dismissOffer(transferId)
}
