package com.fserver.app.presentation.designkit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** How a single diagnostic check came back. `Warning` is information, not a failure. */
enum class DkCheckState { Ok, Warning, Failed }

/**
 * One line of the connection diagnostics: a state glyph, what was checked, and the raw
 * technical result underneath in mono — the text a user pastes into a bug report.
 */
@Composable
fun DkStatusRow(
    title: String,
    detail: String,
    state: DkCheckState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DkSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        val tint = when (state) {
            DkCheckState.Ok -> MaterialTheme.colorScheme.primary
            DkCheckState.Warning -> MaterialTheme.colorScheme.onSurfaceVariant
            DkCheckState.Failed -> MaterialTheme.colorScheme.error
        }
        Icon(
            imageVector = when (state) {
                DkCheckState.Ok -> Icons.Default.Check
                DkCheckState.Warning -> Icons.Default.PriorityHigh
                DkCheckState.Failed -> Icons.Default.ErrorOutline
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = DkType.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
