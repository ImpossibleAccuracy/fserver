package com.fserver.app.presentation.designkit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.theme.FServerTheme

/**
 * Nocturne filter chip: transparent container, the selected one marked by an accent outline and
 * label rather than a fill.
 *
 * Use it for a filter row that can grow or scroll; [DkSegmentedControl] stays the shape for a
 * fixed, boxed-in choice.
 */
@Composable
fun DkFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme

    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        label = { Text(text = text, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = icon?.let {
            {
                Icon(
                    modifier = Modifier.size(16.dp),
                    imageVector = it,
                    contentDescription = null,
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = colors.onSurfaceVariant,
            iconColor = colors.onSurfaceVariant,
            selectedContainerColor = Color.Transparent,
            selectedLabelColor = colors.primary,
            selectedLeadingIconColor = colors.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.outline,
            selectedBorderColor = colors.primary,
            selectedBorderWidth = 1.dp,
        ),
    )
}

@Preview
@Composable
private fun DkFilterChipPreview() {
    FServerTheme {
        DkSurfacePreview {
            var selected by remember { mutableIntStateOf(1) }

            Row(
                modifier = Modifier.padding(DkSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                DkFilterChip(
                    text = "All",
                    selected = selected == 0,
                    onClick = { selected = 0 },
                )
                DkFilterChip(
                    text = "On this phone",
                    selected = selected == 1,
                    onClick = { selected = 1 },
                    icon = Icons.Default.Smartphone,
                )
                DkFilterChip(
                    text = "On other devices",
                    selected = selected == 2,
                    onClick = { selected = 2 },
                    icon = Icons.Default.Cloud,
                )
            }
        }
    }
}
