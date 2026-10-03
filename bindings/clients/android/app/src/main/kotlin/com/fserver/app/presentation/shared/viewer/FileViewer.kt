package com.fserver.app.presentation.shared.viewer

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.viewer.ImageViewer
import com.fserver.app.presentation.shared.viewer.viewer.MediaViewer
import com.fserver.app.presentation.shared.viewer.viewer.NotificationsButton
import com.fserver.app.presentation.shared.viewer.viewer.PdfViewer
import com.fserver.app.presentation.shared.viewer.viewer.TextViewer
import com.fserver.app.presentation.shared.viewer.viewer.openInSystemViewer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Hosts the full-screen file viewer over [content], and the shared-element scope that lets a
 * [FileThumbnail] anywhere in [content] grow into it.
 *
 * [viewed] is what is on screen, null for nothing; [onClose] is asked to clear it.
 */
@Composable
fun FileViewerHost(
    modifier: Modifier = Modifier,
    viewed: FileBrowserUi.File?,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentViewed by rememberUpdatedState(viewed)

    SharedTransitionLayout(modifier = modifier) {
        val transition = remember(this) { FileViewerTransition(this) { currentViewed } }

        CompositionLocalProvider(LocalFileViewerTransition provides transition) {
            content()

            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = viewed,
                contentKey = { it?.locator },
                transitionSpec = { fadeIn() togetherWith fadeOut() using null },
                label = "FileViewer",
            ) { file ->
                if (file != null) {
                    FileViewer(
                        file = file,
                        animatedVisibilityScope = this,
                        onClose = onClose,
                    )
                }
            }
        }
    }
}

/**
 * Black backdrop, the file centred on it, its name on top. A tap shows or hides the name along
 * with any player controls; while something plays they also hide on their own, once nothing has
 * been touched and the player has not changed state for a while.
 *
 * A vertical swipe either way drags the file along and fades the backdrop out; let go far or fast
 * enough and the viewer closes. A predictive back gesture shrinks the file instead, the way the
 * system shrinks a whole screen.
 */
@Composable
private fun FileViewer(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClose: () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var lastActivity by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(showControls, isPlaying, lastActivity) {
        if (showControls && isPlaying) {
            delay(ControlsTimeoutMs.milliseconds)
            showControls = false
        }
    }

    val dragProgress = {
        if (height == 0) 0f else (abs(offset) / (height * FadeDistance)).coerceIn(0f, 1f)
    }
    val progress = { maxOf(dragProgress(), backProgress) }

    PredictiveBackHandler { events ->
        try {
            events.collect { backProgress = it.progress }
            onClose()
        } catch (e: CancellationException) {
            scope.launch {
                animate(initialValue = backProgress, targetValue = 0f) { value, _ ->
                    backProgress = value
                }
            }
            throw e
        }
    }

    LightSystemBarIcons()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height }
            .drawBehind { drawRect(Color.Black, alpha = 1f - progress()) }
            .onEveryPointerEvent { lastActivity = it }
            .clickable(interactionSource = null, indication = null) {
                showControls = !showControls
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { offset += it },
                onDragStopped = { velocity ->
                    if (abs(offset) > height * DismissDistance || abs(velocity) > DismissVelocity) {
                        onClose()
                    } else {
                        animate(initialValue = offset, targetValue = 0f) { value, _ ->
                            offset = value
                        }
                    }
                },
            ),
    ) {
        val contentModifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(x = 0, y = offset.roundToInt()) }
            .shrinkBy { backProgress * BackShrink }

        when (file.kind) {
            FileKindUi.Image -> ImageViewer(
                modifier = contentModifier,
                file = file,
                animatedVisibilityScope = animatedVisibilityScope,
            )

            FileKindUi.Video, FileKindUi.Audio -> MediaViewer(
                modifier = contentModifier,
                file = file,
                animatedVisibilityScope = animatedVisibilityScope,
                showControls = showControls,
                chromeAlpha = { 1f - progress() },
                onPlaybackChanged = { playing, at ->
                    isPlaying = playing
                    lastActivity = at
                },
            )

            FileKindUi.Pdf -> PdfViewer(modifier = contentModifier, file = file)

            FileKindUi.Text -> TextViewer(modifier = contentModifier, file = file)

            FileKindUi.Folder, FileKindUi.Document, FileKindUi.Other -> Unit
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopCenter),
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ViewerTitle(
                modifier = Modifier.graphicsLayer { alpha = 1f - progress() },
                name = file.name,
                onClose = onClose,
                actions = {
                    if (file.kind == FileKindUi.Video || file.kind == FileKindUi.Audio) {
                        NotificationsButton()
                    }
                    if (file.kind == FileKindUi.Pdf) {
                        OpenElsewhereButton(file = file)
                    }
                },
            )
        }
    }
}

/**
 * Reports the uptime of every pointer event on or inside this node, without consuming any: taps on
 * the player's own buttons and slider count as activity too.
 */
private fun Modifier.onEveryPointerEvent(onEvent: (Long) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull()?.let { onEvent(it.uptimeMillis) }
        }
    }
}

/**
 * Lays the content out [fraction] smaller, centred. A real layout change rather than a scale, so
 * a shared element closing mid-gesture starts from where the user sees it.
 */
private fun Modifier.shrinkBy(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val scale = 1f - fraction()
    val width = (constraints.maxWidth * scale).roundToInt()
    val height = (constraints.maxHeight * scale).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(width, height))

    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(
            x = (constraints.maxWidth - width) / 2,
            y = (constraints.maxHeight - height) / 2,
        )
    }
}

@Composable
private fun ViewerTitle(
    modifier: Modifier = Modifier,
    name: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = DkSpacing.xs, vertical = DkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = Color.White,
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = DkSpacing.lg),
        )
        actions()
    }
}

@Composable
private fun OpenElsewhereButton(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
) {
    val context = LocalContext.current

    IconButton(modifier = modifier, onClick = { context.openInSystemViewer(file) }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.file_viewer_open_elsewhere),
            tint = Color.White,
        )
    }
}

/** Dark icons would vanish on the black backdrop; the app's own choice comes back on close. */
@Composable
private fun LightSystemBarIcons() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current

    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars

        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}

/** Room the title takes under the status bar: a 48dp icon button and its padding. */
internal val ViewerTitleHeight = 56.dp

/** How long the controls stay up untouched while something plays. */
private const val ControlsTimeoutMs = 3_000L

/** How much smaller the content gets at the end of a predictive back gesture. */
private const val BackShrink = 0.15f

/** Share of the height a drag takes to fade the backdrop out completely. */
private const val FadeDistance = 0.5f

/** Share of the height a released drag must have covered to close the viewer. */
private const val DismissDistance = 0.15f

/** Px/s: a fling this fast closes the viewer however short it was. */
private const val DismissVelocity = 2_000f
