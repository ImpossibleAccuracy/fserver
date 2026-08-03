package com.fserver.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Nocturne type: Inter for both heading and body roles, headings at weight 500 with
 * tight tracking (-0.015em) and 1.12 line-height, body at 1.55. Inter is not bundled,
 * so the platform sans stands in — the metrics below carry the system's proportions.
 */
private val Heading = FontFamily.SansSerif
private val Body = FontFamily.SansSerif

val FServerTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Heading, fontWeight = FontWeight.Medium,
        fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Heading, fontWeight = FontWeight.Medium,
        fontSize = 25.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Heading, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Heading, fontWeight = FontWeight.Medium,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Heading, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.25).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Heading, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 13.5f.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 12.5f.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Heading, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 17.sp, letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Heading, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Heading, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp,
    ),
)

/** Nocturne radii: `--radius-sm` 4, `--radius-md` 8, `--radius-lg` 14. */
val FServerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
)
