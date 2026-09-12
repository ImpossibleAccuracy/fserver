package com.fserver.app.presentation.designkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

/**
 * Hatched slot for art that does not exist yet — onboarding illustrations, the camera
 * viewfinder. Deliberately unlike any real component so nobody mistakes it for one.
 */
@Composable
fun DkPlaceholderBox(
    modifier: Modifier = Modifier,
    label: String,
    dashedBorder: Boolean = true,
    content: @Composable (() -> Unit)? = null,
) {
    val hatchColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .dkHatch(hatchColor)
            .then(
                if (dashedBorder) Modifier.dkDashedBorder(borderColor) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        }
        Text(
            text = label,
            style = DkType.mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Skeleton block used to sketch the screen sitting behind a modal. */
@Composable
fun DkSkeletonBlock(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(modifier = modifier.background(color, MaterialTheme.shapes.medium))
}
