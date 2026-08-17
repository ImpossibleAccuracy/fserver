package com.fserver.app.presentation.screens.settings.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.settings.pin.model.PIN_LENGTH
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeIntent
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeState
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun PinChangeScreen(
    viewModel: PinChangeViewModel = koinViewModel(),
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.uiEffects) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                PinChangeUiEffect.NavigateBack -> navigateUp()
            }
        }
    }

    PinChangeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

/**
 * Its own keypad rather than the system keyboard: a PIN typed into a text field goes through
 * Android's suggestion and clipboard machinery, which is not somewhere a PIN belongs.
 */
@Composable
private fun PinChangeScreen(
    state: PinChangeState,
    onIntent: (PinChangeIntent) -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.pin_title),
                onBack = navigateUp,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = DkSpacing.xxl, vertical = DkSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
            ) {
                Text(
                    text = stringResource(
                        when (state.step) {
                            PinChangeState.Step.New -> R.string.pin_step_new
                            PinChangeState.Step.Repeat -> R.string.pin_step_repeat
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        when {
                            state.isMismatch -> R.string.pin_mismatch
                            state.step == PinChangeState.Step.New -> R.string.pin_step_new_desc
                            else -> R.string.pin_step_repeat_desc
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.isMismatch) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )

                PinDots(filled = state.filled)
            }

            Box(modifier = Modifier.weight(1f))

            Keypad(
                onDigit = { onIntent(PinChangeIntent.DigitPressed(it)) },
                onBackspace = { onIntent(PinChangeIntent.BackspacePressed) },
                onCancel = navigateUp,
            )
        }
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(
        modifier = Modifier.padding(vertical = DkSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        repeat(PIN_LENGTH) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .then(
                        if (isFilled) {
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
private fun Keypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            horizontal = DkSpacing.screenPadding,
            vertical = DkSpacing.lg,
        ),
    ) {
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
            KeypadKey(modifier = Modifier.weight(1f), onClick = onCancel) {
                DkCaption(text = stringResource(R.string.action_cancel))
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

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun PinChangeScreenPreview() {
    FServerTheme {
        PinChangeScreen(
            state = PinChangeState(step = PinChangeState.Step.New, filled = 3),
            onIntent = {},
            navigateUp = {},
        )
    }
}
