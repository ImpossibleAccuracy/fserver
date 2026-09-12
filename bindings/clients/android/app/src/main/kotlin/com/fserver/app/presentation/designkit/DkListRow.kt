package com.fserver.app.presentation.designkit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 34dp rounded square that fronts every device, folder and file row. It stands in for
 * a thumbnail until one is loaded, so rows never change height when previews arrive.
 */
@Composable
fun DkThumbnail(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: Dp = 34.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.shapes.medium,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(size / 2),
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DkIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    size: Dp = 18.dp,
    contentDescription: String? = null,
) {
    Icon(
        modifier = modifier.size(size),
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Generic list row: thumbnail (or any leading slot), title, one line of secondary text,
 * trailing slot. Devices, folders and files all reduce to this shape in the deck.
 */
@Composable
fun DkListRow(
    modifier: Modifier = Modifier,
    title: String,
    titleMaxLines: Int = 1,
    subtitle: String? = null,
    subtitleStyle: TextStyle? = null,
    subtitleColor: Color? = null,
    subtitleMaxLines: Int = 1,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentPaddings: PaddingValues = PaddingValues(
        horizontal = DkSpacing.screenPadding,
        vertical = DkSpacing.md
    ),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPaddings),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        val alpha = if (dimmed) 0.5f else 1f
        if (leading != null) {
            Box(modifier = Modifier.alphaIf(dimmed)) { leading() }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = subtitleStyle ?: MaterialTheme.typography.labelSmall,
                    color = (subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                        .copy(alpha = alpha),
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.alphaIf(dimmed)) { trailing() }
        }
    }
}

private fun Modifier.alphaIf(dimmed: Boolean): Modifier =
    if (dimmed) this.alpha(0.5f) else this
