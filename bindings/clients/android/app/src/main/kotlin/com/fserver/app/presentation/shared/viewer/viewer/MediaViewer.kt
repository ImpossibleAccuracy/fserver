package com.fserver.app.presentation.shared.viewer.viewer

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.material3.Player
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.ui.compose.material3.indicator.DurationText
import androidx.media3.ui.compose.material3.indicator.PositionText
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.fserver.app.data.playback.BackgroundPlayback
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.fileViewerContent
import com.fserver.app.presentation.shared.viewer.imageModel
import com.fserver.app.presentation.shared.viewer.impl.SourceFileDataSource
import com.fserver.app.presentation.shared.viewer.impl.locatorUri
import com.fserver.app.presentation.shared.viewer.thumbnailCacheKey
import com.fserver.core.files.FilesController
import org.koin.compose.koinInject

/**
 * Plays a video or an audio file, starting right away. [showControls] is owned by the viewer, so
 * the title and the controls come and go together; [onPlaybackChanged] tells it whether anything
 * plays and when the player last did something, with the event's uptime.
 *
 * The video renders into a `TextureView`: a `SurfaceView` sits outside the view's own drawing,
 * so it would neither follow the swipe nor take part in the shared-element transition.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
internal fun MediaViewer(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
    animatedVisibilityScope: AnimatedVisibilityScope,
    showControls: Boolean,
    chromeAlpha: () -> Float,
    onPlaybackChanged: (isPlaying: Boolean, atUptimeMs: Long) -> Unit,
) {
    val player = rememberViewerPlayer(file)
    val currentOnPlaybackChanged by rememberUpdatedState(onPlaybackChanged)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(*PlaybackEvents)) {
                    currentOnPlaybackChanged(player.isPlaying, SystemClock.uptimeMillis())
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val isVideo = file.kind == FileKindUi.Video

    Player(
        modifier = modifier
            .then(if (isVideo) Modifier.fileViewerContent(file, animatedVisibilityScope) else Modifier),
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        shutter = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = chromeAlpha() },
            ) {
                if (isVideo) {
                    VideoPoster(file = file)
                } else {
                    AudioArtwork(file = file, animatedVisibilityScope = animatedVisibilityScope)
                }
            }
        },
        showControls = showControls,
        topControls = null,
        centerControls = { player, visible ->
            PlayerDefaults.CenterControls(
                modifier = Modifier.graphicsLayer { alpha = chromeAlpha() },
                player = player,
                visible = visible,
                backSecondary = {},
                forwardSecondary = {},
            )
        },
        bottomControls = { player, visible ->
            Timeline(
                modifier = Modifier.graphicsLayer { alpha = chromeAlpha() },
                player = player,
                visible = visible,
            )
        },
    )
}

/** Position, seek bar, duration. No scrim: the backdrop is black already. */
@OptIn(UnstableApi::class)
@Composable
private fun Timeline(
    modifier: Modifier = Modifier,
    player: Player?,
    visible: Boolean,
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            PositionText(player = player, color = Color.White)
            ProgressSlider(player = player, modifier = Modifier.weight(1f))
            DurationText(player = player, color = Color.White)
        }
    }
}

/** The first frame from the tile, until the player draws one of its own. */
@Composable
private fun VideoPoster(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
) {
    val context = LocalPlatformContext.current
    val request = remember(context, file.locator) {
        ImageRequest.Builder(context)
            .data(file.imageModel())
            .placeholderMemoryCacheKey(file.thumbnailCacheKey)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize(),
    )
}

/** The picture from the file's tags, the one its tile shows; an icon when it has none. */
@Composable
private fun AudioArtwork(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val context = LocalPlatformContext.current
    val request = remember(context, file.locator) {
        ImageRequest.Builder(context)
            .data(file.imageModel(acceptCache = false))
            .placeholderMemoryCacheKey(file.thumbnailCacheKey)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    val state by painter.state.collectAsState()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (state is AsyncImagePainter.State.Error) {
            Icon(
                modifier = Modifier.size(ArtworkIconSize),
                imageVector = Icons.Default.AudioFile,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
            )
        } else {
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .fileViewerContent(file, animatedVisibilityScope)
                    .clip(MaterialTheme.shapes.medium),
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * Released with the viewer. When the app leaves the screen, playback goes on under a media
 * notification, or pauses if notifications are off.
 */
@Composable
private fun rememberViewerPlayer(file: FileBrowserUi.File): Player {
    val context = LocalContext.current
    val playback = koinInject<BackgroundPlayback>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val files = koinInject<FilesController>()
    val player = remember(context, file.locator) { context.viewerPlayer(file, files) }
    val session = remember(player) { playback.newSession(player) }

    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> playback.stop(session)
                Lifecycle.Event.ON_STOP -> if (!player.playWhenReady || !playback.start(session)) player.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            playback.stop(session)
            session.release()
            player.release()
        }
    }

    return player
}

/** A source's file plays through `:core`, so an encrypted one plays too; a scanned one by path. */
@OptIn(UnstableApi::class)
private fun Context.viewerPlayer(file: FileBrowserUi.File, files: FilesController): Player =
    ExoPlayer.Builder(this)
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()
        .apply {
            val item = MediaItem.Builder()
                .setMediaMetadata(MediaMetadata.Builder().setDisplayTitle(file.name).build())
            val key = file.key
            val locator = file.locator

            when {
                locator == null -> Unit
                key != null -> setMediaSource(
                    ProgressiveMediaSource.Factory(SourceFileDataSource.Factory(files))
                        .createMediaSource(item.setUri(SourceFileDataSource.uriOf(key)).build()),
                )

                else -> setMediaItem(item.setUri(locatorUri(locator)).build())
            }
            prepare()
            playWhenReady = true
        }

/** What counts as the player doing something: play/pause, a seek, buffering or the end. */
private val PlaybackEvents = intArrayOf(
    Player.EVENT_IS_PLAYING_CHANGED,
    Player.EVENT_PLAY_WHEN_READY_CHANGED,
    Player.EVENT_POSITION_DISCONTINUITY,
    Player.EVENT_PLAYBACK_STATE_CHANGED,
)

private val ArtworkIconSize = 96.dp
