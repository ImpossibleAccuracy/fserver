package com.fserver.app.presentation.designkit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.theme.FServerTheme

/**
 * A few exclusive values in one hairline box — presets and the like. Unlike
 * [DkSegmentedControl], the selected cell is a filled accent block inset in the box, so a value
 * row reads as one control rather than a row of buttons.
 */
@Composable
fun <T> DkChoiceBar(
    modifier: Modifier = Modifier,
    options: List<DkSegmentedOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(DkSpacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                    .clickable { onSelect(option.value) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                    }
                    .padding(vertical = DkSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun DkChoiceBarPreview() {
    FServerTheme {
        DkSurfacePreview {
            DkChoiceBar(
                modifier = Modifier.padding(DkSpacing.lg),
                options = listOf("1 GB", "5 GB", "10 GB", "Custom").map {
                    DkSegmentedOption(value = it, label = it)
                },
                selected = "5 GB",
                onSelect = {},
            )
        }
    }
}
