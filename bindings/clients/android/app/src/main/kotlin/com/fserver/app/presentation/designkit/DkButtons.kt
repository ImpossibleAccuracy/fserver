package com.fserver.app.presentation.designkit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.theme.FServerTheme

/**
 * Nocturne buttons are outline-first: nothing on a screen is a filled block of accent.
 * Primary carries the accent border and label, secondary the neutral divider border,
 * ghost is label-only.
 */

private val DkButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

@Composable
fun DkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = DkButtonPadding,
    ) {
        DkButtonContent(text, icon)
    }
}

@Composable
fun DkSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = DkButtonPadding,
    ) {
        DkButtonContent(text, icon)
    }
}

@Composable
fun DkGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = DkButtonPadding,
    ) {
        DkButtonContent(text, icon)
    }
}

/**
 * The pill that floats over the file browser. Same accent outline as [DkPrimaryButton],
 * fully rounded, sitting on the screen ground rather than on a surface.
 */
@Composable
fun DkPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        DkButtonContent(text, icon)
    }
}

@Composable
private fun DkButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 0.dp),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = if (icon != null) Modifier.padding(start = DkSpacing.sm) else Modifier,
    )
}

@Preview
@Composable
private fun DkButtonsPreview() {
    FServerTheme {
        DkSurfacePreview {
            Column(
                modifier = Modifier.padding(DkSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkPrimaryButton("Fingerprints match — connect", {}, Modifier.fillMaxWidth())
                DkSecondaryButton("Scan QR code", {}, Modifier.fillMaxWidth())
                DkGhostButton("Cancel", {}, Modifier.fillMaxWidth())
                DkPillButton("Send file", {})
            }
        }
    }
}
