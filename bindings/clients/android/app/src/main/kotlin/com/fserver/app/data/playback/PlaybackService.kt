package com.fserver.app.data.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.koin.android.ext.android.inject

/** Media notification for [BackgroundPlayback]'s session; lives only while the app is hidden. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val playback: BackgroundPlayback by inject()

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(TitledNotificationProvider(this))
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                playback.session?.let {
                    it.player.pause()
                    playback.stop(it)
                }
            }
        })

        val session = playback.session
        if (session == null) stopSelf() else addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        playback.session

    override fun onDestroy() {
        clearListener()
        sessions.forEach(::removeSession)
        super.onDestroy()
    }
}

/** Falls back to the file name for a file whose tags carry no title. */
@OptIn(UnstableApi::class)
private class TitledNotificationProvider(context: Context) :
    DefaultMediaNotificationProvider(context) {

    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? =
        metadata.title ?: metadata.displayTitle
}
