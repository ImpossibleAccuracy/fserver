package com.fserver.app.presentation.designkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The Nocturne rule: fades to transparent at both ends instead of stopping cleanly.
 * Used between list rows and settings rows. Box outlines and control separators stay solid.
 */
@Composable
fun DkFadingDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    inset: Float = 0.075f,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(dkFadingBrush(color, inset)),
    )
}
