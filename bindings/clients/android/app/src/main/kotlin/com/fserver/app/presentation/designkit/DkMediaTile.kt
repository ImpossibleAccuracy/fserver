package com.fserver.app.presentation.designkit

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Square tile of the media grid. Media gets a thumbnail slot, anything else falls back to
 * its extension on a dimmer ground so a mixed folder still reads as one grid.
 *
 * Previews are fetched and cached ahead of time; the file itself is not — [badge] is where
 * the caller says so, via [DkMediaTileBadge].
 *
 * [thumbnail] is drawn clipped to the tile, and loads however the caller likes; until it has
 * something the tile is the same empty square, so a grid never reflows as previews arrive.
 *
 * [label] is one small line along the bottom, over a scrim so it reads on any thumbnail.
 * [onLongClick] is the tile's secondary gesture, as on [DkListRow].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DkMediaTile(
    modifier: Modifier = Modifier,
    extensionLabel: String? = null,
    durationLabel: String? = null,
    label: String? = null,
    thumbnail: (@Composable BoxScope.() -> Unit)? = null,
    badge: @Composable (BoxScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val isMedia = extensionLabel == null

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                if (isMedia) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                RoundedCornerShape(6.dp),
            )
            .then(
                when {
                    onLongClick != null -> Modifier.combinedClickable(
                        onClick = onClick ?: {},
                        onLongClick = onLongClick,
                    )

                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            ),
    ) {
        if (thumbnail != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(6.dp)),
                content = thumbnail,
            )
        }
        if (extensionLabel != null) {
            Text(
                text = extensionLabel,
                style = DkType.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (durationLabel != null) {
            Text(
                text = durationLabel,
                style = DkType.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (label != null) {
            Text(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, LabelScrim)))
                    .padding(horizontal = DkSpacing.xs, vertical = DkSpacing.xxs),
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        badge?.invoke(this)
    }
}

private val LabelScrim = Color.Black.copy(alpha = 0.6f)

/**
 * Corner marker for a tile — availability, a pin, a selection tick. Placed bottom-right so it
 * never covers the middle of a thumbnail.
 */
@Composable
fun BoxScope.DkMediaTileBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.tertiary,
    contentDescription: String? = null,
) {
    Icon(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(5.dp)
            .size(12.dp),
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
    )
}
