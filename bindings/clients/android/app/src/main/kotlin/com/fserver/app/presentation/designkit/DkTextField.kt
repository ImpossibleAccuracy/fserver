package com.fserver.app.presentation.designkit

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Nocturne field: the label lives above the control (not floating inside it), the input
 * itself is a surface block with a divider hairline that turns accent on focus.
 */
@Composable
fun DkTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = DkSpacing.xs),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.titleSmall,
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/** How loudly a [DkInfoBox] reads: an explanation, or something that went wrong. */
enum class DkInfoTone { Quiet, Alert }

/**
 * Bordered note block.
 *
 * [DkInfoTone.Quiet] is the deck's explanatory box; [DkInfoTone.Alert] is the same shape in the
 * error colour, for a state the user is expected to act on. [title] is optional and reads as the
 * one-line summary above the body.
 */
@Composable
fun DkInfoBox(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: DkInfoTone = DkInfoTone.Quiet,
) {
    val colors = MaterialTheme.colorScheme
    val accent = when (tone) {
        DkInfoTone.Quiet -> colors.outline
        DkInfoTone.Alert -> colors.error
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = accent,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = when (tone) {
                DkInfoTone.Quiet -> colors.onSurfaceVariant
                DkInfoTone.Alert -> colors.onSurface
            },
        )
    }
}
