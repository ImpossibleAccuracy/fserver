package com.fserver.app.presentation.screens.settings.pin

import androidx.compose.runtime.getValue
import com.fserver.app.presentation.composable.ObserveEffects
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkKeypad
import com.fserver.app.presentation.designkit.DkPinDots
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.settings.pin.model.PIN_LENGTH
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeIntent
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeState
import com.fserver.app.presentation.screens.settings.pin.model.PinChangeUiEffect
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PinChangeScreen(
    key: Destination.Settings.PinChange,
    viewModel: PinChangeViewModel = koinViewModel { parametersOf(key) },
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.uiEffects) { effect ->
        when (effect) {
            PinChangeUiEffect.NavigateBack -> navigateUp()
        }
    }

    PinChangeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        navigateUp = navigateUp,
    )
}

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

                DkPinDots(filled = state.filled, length = PIN_LENGTH)
            }

            Box(modifier = Modifier.weight(1f))

            DkKeypad(
                modifier = Modifier.padding(
                    horizontal = DkSpacing.screenPadding,
                    vertical = DkSpacing.lg,
                ),
                onDigit = { onIntent(PinChangeIntent.DigitPressed(it)) },
                onBackspace = { onIntent(PinChangeIntent.BackspacePressed) },
                onCancel = navigateUp,
            )
        }
    }
}

@Preview(showBackground = true)
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
