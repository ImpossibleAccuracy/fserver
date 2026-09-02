package com.fserver.app.presentation.designkit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One line of the tree view. Depth is drawn as a guideline plus indent — the whole
 * structure stays on screen, which is the point of this view, at the cost of a narrower
 * touch target than the list view offers.
 */
@Composable
fun DkTreeRow(
    title: String,
    modifier: Modifier = Modifier,
    expandable: Boolean = false,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    contentPaddings: PaddingValues = PaddingValues(horizontal = DkSpacing.xs, vertical = 8.dp),
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPaddings),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (expandable) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(modifier = Modifier.weight(1f))
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) trailing()
    }
}
