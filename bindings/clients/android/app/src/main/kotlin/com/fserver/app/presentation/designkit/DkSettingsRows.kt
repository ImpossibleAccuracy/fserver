package com.fserver.app.presentation.designkit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Settings rows. All three share one skeleton — title, optional supporting line,
 * trailing control — so a group reads as a single column no matter what it mixes.
 *
 * The screen gutter is inside the row, as in [DkListRow]: the row spans the full width so its
 * click target and ripple reach the screen edges, and only the content is inset. Callers therefore
 * put rows in an ungutter'd column and pad the surrounding blocks themselves.
 */
@Composable
fun DkSettingsRow(
    modifier: Modifier = Modifier,
    title: String,
    supportingText: String? = null,
    accented: Boolean = false,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.md),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (accented) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = DkSpacing.xxs),
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * The kit's switch. Split out of [DkSwitchRow] because the settings deck also puts one next to a
 * "Change" action, and a second copy of these colours would drift from this one.
 */
@Composable
fun DkSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Row whose trailing slot is a switch; the whole row toggles it. */
@Composable
fun DkSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    DkSettingsRow(
        modifier = modifier,
        title = title,
        supportingText = supportingText,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            DkSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

/** Row that opens another screen: optional value on the right, then a chevron. */
@Composable
fun DkNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    supportingText: String? = null,
    accented: Boolean = false,
) {
    DkSettingsRow(
        modifier = modifier,
        title = title,
        supportingText = supportingText,
        accented = accented,
        onClick = onClick,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
            ) {
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

/** Row that only reports a value — no target, no control. */
@Composable
fun DkValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    DkSettingsRow(
        modifier = modifier,
        title = title,
        trailing = {
            Text(
                text = value,
                style = DkType.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
