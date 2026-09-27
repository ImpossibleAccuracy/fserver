package com.fserver.app.presentation.screens.source.shared.preferences.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkType

/** A value with −/+ around it. [onStep] gets -1 or 1; clamping is the caller's. */
@Composable
fun ValueStepper(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean,
    onStep: (Int) -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        DkIconButton(
            icon = Icons.Default.Remove,
            onClick = { if (enabled) onStep(-1) },
        )
        Text(
            modifier = Modifier.widthIn(min = 84.dp),
            text = label,
            style = DkType.monoLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        DkIconButton(
            icon = Icons.Default.Add,
            onClick = { if (enabled) onStep(1) },
        )
    }
}
