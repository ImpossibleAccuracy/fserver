package com.fserver.app.presentation.designkit

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Nocturne spacing scale. The CSS scale is 2.8px-based; these are its steps snapped to
 * Android's 4dp grid, which is what every layout in the kit measures against.
 */
object DkSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Horizontal page gutter used by every screen body in the deck. */
    val screenPadding: Dp = 16.dp
}

/**
 * Type roles the Material scale has no slot for: the monospaced technical voice that
 * Nocturne uses for addresses, fingerprints, sizes and protocol details.
 */
object DkType {
    val mono: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )

    val monoLarge: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

    /** The fingerprint block — read aloud character by character, so it runs large. */
    val monoFingerprint: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
}

/**
 * A rule that fades to transparent at both ends instead of stopping cleanly — a Nocturne
 * signature. [inset] is the fraction of the width the ramp takes on each end.
 */
fun dkFadingBrush(color: Color, inset: Float = 0.075f): Brush = Brush.horizontalGradient(
    0f to Color.Transparent,
    inset to color,
    1f - inset to color,
    1f to Color.Transparent,
)

/**
 * Nocturne's dashed hairline — the outline it gives anything provisional: a placeholder slot,
 * a result row not filled in yet, a network card whose details the OS is still withholding.
 */
fun Modifier.dkDashedBorder(
    color: Color,
    cornerRadius: Dp = 8.dp,
    dash: Dp = 6.dp,
): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())),
        ),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
    )
}

/**
 * The 45° hatch Nocturne fills placeholder surfaces with (illustration slots, the camera
 * viewfinder) so an unbuilt area never reads as an empty component.
 */
fun Modifier.dkHatch(color: Color, stripe: Dp = 8.dp): Modifier = drawBehind {
    val step = stripe.toPx() * 2
    val strokeWidth = stripe.toPx()
    var x = -size.height
    while (x < size.width + size.height) {
        drawLine(
            color = color,
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = strokeWidth,
        )
        x += step
    }
}
