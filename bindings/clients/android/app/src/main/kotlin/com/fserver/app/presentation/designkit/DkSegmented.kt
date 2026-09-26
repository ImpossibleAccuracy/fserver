package com.fserver.app.presentation.designkit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class DkSegmentedOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
)

private val CompactHeight = 30.dp
private val CompactPadding = PaddingValues(horizontal = DkSpacing.md)

/**
 * Nocturne segmented control: hairline box, the selected option marked by an accent
 * inset outline rather than a fill. [compact] shrinks it to sit beside a list's own controls.
 */
@Composable
fun <T> DkSegmentedControl(
    options: List<DkSegmentedOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelsVisible: Boolean = true,
    compact: Boolean = false,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            SegmentedButton(
                modifier = if (compact) Modifier.height(CompactHeight) else Modifier,
                contentPadding = if (compact) CompactPadding else SegmentedButtonDefaults.ContentPadding,
                selected = isSelected,
                onClick = { onSelect(option.value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Color.Transparent,
                    activeContentColor = MaterialTheme.colorScheme.primary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline,
                ),
                icon = {
                    if (option.icon != null) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            ) {
                if (labelsVisible) {
                    Text(
                        text = option.label,
                        style = if (compact) {
                            MaterialTheme.typography.labelSmall
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                    )
                }
            }
        }
    }
}
