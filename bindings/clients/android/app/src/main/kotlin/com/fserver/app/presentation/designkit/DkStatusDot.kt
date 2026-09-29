package com.fserver.app.presentation.designkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small filled circle: online state, legend swatch, sync marker. */
@Composable
fun DkStatusDot(
    modifier: Modifier = Modifier,
    color: Color,
    size: Dp = 6.dp,
) {
    Box(modifier = modifier.size(size).background(color, CircleShape))
}
