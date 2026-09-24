package com.fserver.app.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import timber.log.Timber
import java.util.UUID

/**
 * Hands the viewer's player to [PlaybackService] while the app is out of sight. The player stays
 * owned by the viewer; the service only holds the session that puts it in a notification.
 */
class BackgroundPlayback(private val context: Context) {

    internal var session: MediaSession? = null
        private set

    /** A session for [player], opening the app from the notification. Released by the caller. */
    fun newSession(player: Player): MediaSession {
        val builder = MediaSession.Builder(context, player)
            // Unique: the closing viewer's session outlives the next one's creation by an animation.
            .setId(UUID.randomUUID().toString())
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE),
            )
        }
        return builder.build()
    }

    /** Shows [session] in a notification. False when notifications are off: nothing is shown. */
    fun start(session: MediaSession): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        this.session = session
        return try {
            context.startService(Intent(context, PlaybackService::class.java))
            true
        } catch (e: IllegalStateException) {
            Timber.w(e, "Background playback not allowed")
            this.session = null
            false
        }
    }

    /** Takes the notification down, if it is [session]'s. */
    fun stop(session: MediaSession) {
        if (this.session !== session) return
        this.session = null
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}
