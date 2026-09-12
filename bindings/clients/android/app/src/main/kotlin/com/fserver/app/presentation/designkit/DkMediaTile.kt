package com.fserver.app.presentation.designkit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Square tile of the media grid. Media gets a thumbnail slot, anything else falls back to
 * its extension on a dimmer ground so a mixed folder still reads as one grid.
 *
 * Previews are fetched and cached ahead of time; the file itself is not — [badge] is where
 * the caller says so, via [DkMediaTileBadge].
 *
 * [thumbnail] fills the slot once one is decoded; until then the tile is the same empty square,
 * so a grid never reflows as previews arrive.
 */
@Composable
fun DkMediaTile(
    modifier: Modifier = Modifier,
    extensionLabel: String? = null,
    durationLabel: String? = null,
    thumbnail: ImageBitmap? = null,
    badge: @Composable (BoxScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(6.dp)),
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
        badge?.invoke(this)
    }
}

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
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(5.dp)
            .size(12.dp),
    )
}
