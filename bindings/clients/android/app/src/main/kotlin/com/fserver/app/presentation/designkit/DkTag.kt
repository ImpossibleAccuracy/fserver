package com.fserver.app.presentation.designkit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.theme.FServerTheme

enum class DkTagStyle { Accent, Secondary, Neutral, Outline }

/** Small state label: online / offline / queued / on. Never interactive. */
@Composable
fun DkTag(
    text: String,
    modifier: Modifier = Modifier,
    style: DkTagStyle = DkTagStyle.Neutral,
) {
    val colors = MaterialTheme.colorScheme
    val container = when (style) {
        DkTagStyle.Accent -> colors.primaryContainer
        DkTagStyle.Secondary -> colors.secondaryContainer
        DkTagStyle.Neutral -> colors.surfaceContainerHigh
        DkTagStyle.Outline -> Color.Transparent
    }
    val content = when (style) {
        DkTagStyle.Accent -> colors.onPrimaryContainer
        DkTagStyle.Secondary -> colors.onSecondaryContainer
        DkTagStyle.Neutral -> colors.onSurface
        DkTagStyle.Outline -> colors.primary
    }
    val shape = RoundedCornerShape(6.dp)

    Row(
        modifier = modifier
            .background(container, shape)
            .then(
                if (style == DkTagStyle.Outline) {
                    Modifier.border(BorderStroke(1.dp, colors.primary), shape)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Preview
@Composable
private fun DkTagPreview() {
    FServerTheme {
        DkSurfacePreview {
            Row(
                modifier = Modifier.padding(DkSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkTag("Online", style = DkTagStyle.Accent)
                DkTag("Offline", style = DkTagStyle.Neutral)
                DkTag("≤1000 files", style = DkTagStyle.Outline)
            }
        }
    }
}
