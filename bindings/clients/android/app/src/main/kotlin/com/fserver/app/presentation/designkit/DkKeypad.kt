package com.fserver.app.presentation.designkit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R

/**
 * Numeric keypad for PINs and codes.
 *
 * Its own keys rather than the system keyboard: digits typed into a text field go through
 * Android's suggestion and clipboard machinery, which is not somewhere a secret belongs.
 * [onCancel] fills the bottom-left key; without it the slot stays empty.
 */
@Composable
fun DkKeypad(
    modifier: Modifier = Modifier,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        listOf("123", "456", "789").forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { digit ->
                    KeypadKey(modifier = Modifier.weight(1f), onClick = { onDigit(digit) }) {
                        DigitLabel(digit)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            if (onCancel != null) {
                KeypadKey(modifier = Modifier.weight(1f), onClick = onCancel) {
                    DkCaption(text = stringResource(R.string.action_cancel))
                }
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            KeypadKey(modifier = Modifier.weight(1f), onClick = { onDigit('0') }) {
                DigitLabel('0')
            }
            KeypadKey(modifier = Modifier.weight(1f), onClick = onBackspace) {
                DkIcon(
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.pin_delete),
                )
            }
        }
    }
}

/** How many digits of a PIN are in, as [length] dots with the first [filled] lit. */
@Composable
fun DkPinDots(
    modifier: Modifier = Modifier,
    filled: Int,
    length: Int,
) {
    Row(
        modifier = modifier.padding(vertical = DkSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        repeat(length) { index ->
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .then(
                        if (index < filled) {
                            Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        }
                    ),
            )
        }
    }
}

@Composable
private fun KeypadKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = DkSpacing.md),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun DigitLabel(digit: Char) {
    Text(
        text = digit.toString(),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Preview
@Composable
private fun DkKeypadPreview() {
    DkSurfacePreview {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DkPinDots(filled = 3, length = 6)
            DkKeypad(onDigit = {}, onBackspace = {}, onCancel = {})
        }
    }
}
